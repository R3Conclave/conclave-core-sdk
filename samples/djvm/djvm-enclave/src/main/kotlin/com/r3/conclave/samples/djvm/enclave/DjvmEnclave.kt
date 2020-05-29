package com.r3.conclave.samples.djvm.enclave

import com.r3.conclave.common.SHA256Hash
import com.r3.conclave.common.enclave.EnclaveCall
import com.r3.conclave.common.internal.readFully
import com.r3.conclave.enclave.Enclave
import com.r3.conclave.samples.djvm.common.proto.ExecuteTask
import com.r3.conclave.samples.djvm.common.proto.Request
import com.r3.conclave.samples.djvm.common.proto.SendJar
import com.r3.conclave.samples.djvm.common.proto.TaskResult
import com.r3.conclave.utils.classloaders.MemoryClassLoader
import com.r3.conclave.utils.classloaders.MemoryURL
import net.corda.djvm.SandboxConfiguration
import net.corda.djvm.analysis.AnalysisConfiguration
import net.corda.djvm.analysis.Whitelist
import net.corda.djvm.execution.ExecutionProfile
import net.corda.djvm.source.ApiSource
import net.corda.djvm.source.UserPathSource
import net.corda.djvm.source.UserSource
import java.nio.ByteBuffer

class DjvmEnclave : EnclaveCall, Enclave() {
    companion object {
        private val bootstrapJar: MemoryURL
        private val parentConfiguration: SandboxConfiguration

        init {
            val bootstrapJarData = DjvmEnclave::class.java.classLoader.getResourceAsStream("deterministic-rt.zip")!!.readFully()
            bootstrapJar = createMemoryURL(bootstrapJarData)

            val bootstrapClassLoader = DJVMBootstrapClassLoader(listOf(bootstrapJar))
            val rootConfiguration = AnalysisConfiguration.createRoot(
                    userSource = UserPathSource(emptyList()),
                    whitelist = Whitelist.MINIMAL,
                    bootstrapSource = bootstrapClassLoader
            )
            parentConfiguration = SandboxConfiguration.of(
                    ExecutionProfile.UNLIMITED,
                    SandboxConfiguration.ALL_RULES,
                    SandboxConfiguration.ALL_EMITTERS,
                    SandboxConfiguration.ALL_DEFINITION_PROVIDERS,
                    rootConfiguration
            )
        }

        fun createMemoryURL(input: ByteArray): MemoryURL {
            return DJVMMemoryURLStreamHandler.createURL(SHA256Hash.hash(input).toString(), ByteBuffer.wrap(input))
        }
    }

    private val userJars = ArrayList<MemoryURL>()

    override fun invoke(bytes: ByteArray): ByteArray? {
        val request = Request.parseFrom(bytes)
        val response = when (request.requestsCase!!) {
            Request.RequestsCase.SEND_JAR -> {
                receiveJar(request.sendJar)
                null
            }
            Request.RequestsCase.CLEAR_JARS -> {
                DJVMMemoryURLStreamHandler.clear()
                null
            }
            Request.RequestsCase.EXECUTE_TASK -> receiveTask(request.executeTask)
            Request.RequestsCase.REQUESTS_NOT_SET -> throw IllegalArgumentException("requests not set")
        }
        return response?.build()?.toByteArray()
    }

    private fun receiveJar(request: SendJar) {
        userJars += createMemoryURL(request.data.toByteArray())
    }

    private fun receiveTask(request: ExecuteTask): TaskResult.Builder? {
        val djvmMemoryClassLoader = DJVMBootstrapClassLoader(listOf(bootstrapJar))
        val userSource = DJVMUserClassSource(userJars)
        DJVMBase.setupClassLoader(userSource, djvmMemoryClassLoader, emptyList(), parentConfiguration)

        val result = try {
            SandboxRunner().run(request.className, request.input)?.toString()
        } catch (t: Throwable) {
            t.toString()
        }

        DJVMBase.destroyRootContext()

        return TaskResult.newBuilder().apply {
            if (result != null) {
                setResult(result)
            }
        }
    }
}

class DJVMBootstrapClassLoader(memoryURLs: List<MemoryURL>) : MemoryClassLoader(memoryURLs), ApiSource {
    override fun close() {
    }
}

class DJVMUserClassSource(memoryUrls: List<MemoryURL>) : MemoryClassLoader(memoryUrls), UserSource {
    override fun close() {
    }
}
