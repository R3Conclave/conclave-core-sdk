package com.r3.conclave.samples.djvm.enclave

import com.r3.conclave.common.SHA256Hash
import com.r3.conclave.common.enclave.EnclaveCall
import com.r3.conclave.common.internal.getLengthPrefixBytes
import com.r3.conclave.common.internal.getRemainingBytes
import com.r3.conclave.enclave.Enclave
import com.r3.conclave.samples.djvm.common.MessageType
import com.r3.conclave.samples.djvm.common.Status
import com.r3.sgx.utils.classloaders.MemoryClassLoader
import com.r3.sgx.utils.classloaders.MemoryURL
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
            val bootstrapJarData = DjvmEnclave::class.java.classLoader.getResourceAsStream("deterministic-rt.zip")!!.use { it.readBytes() }
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
        val input = ByteBuffer.wrap(bytes)
        val messageType = input.getInt()
        return when (messageType) {
            MessageType.JAR.ordinal -> receiveJar(input)
            MessageType.TASK.ordinal -> receiveTask(input)
            else -> throw IllegalArgumentException("Unknown message type: $messageType")
        }
    }

    private fun receiveJar(input: ByteBuffer): ByteArray? {
        return try {
            val jarBytes = input.getRemainingBytes()
            if (jarBytes.isNotEmpty()) {
                userJars.add(createMemoryURL(jarBytes))
                reply(Status.OK)
            } else {
                DJVMMemoryURLStreamHandler.clear()
                null
            }
        } catch (t: Throwable) {
            reply(Status.FAIL)
        }
    }

    private fun receiveTask(input: ByteBuffer): ByteArray {
        val className = String(input.getLengthPrefixBytes())
        val taskInput = String(input.getRemainingBytes())

        val djvmMemoryClassLoader = DJVMBootstrapClassLoader(listOf(bootstrapJar))
        val userSource = DJVMUserClassSource(userJars)
        DJVMBase.setupClassLoader(userSource, djvmMemoryClassLoader, emptyList(), parentConfiguration)

        val result: Any? = try {
            SandboxRunner().run(className, taskInput)
        } catch (t: Throwable) {
            t.toString()
        }

        DJVMBase.destroyRootContext()

        return result.toString().toByteArray()
    }

    private fun reply(status: Status): ByteArray = ByteBuffer.allocate(Int.SIZE_BYTES).putInt(status.ordinal).array()
}

class DJVMBootstrapClassLoader(memoryURLs: List<MemoryURL>) : MemoryClassLoader(memoryURLs), ApiSource {
    override fun close() {
    }
}

class DJVMUserClassSource(memoryUrls: List<MemoryURL>) : MemoryClassLoader(memoryUrls), UserSource {
    override fun close() {
    }
}
