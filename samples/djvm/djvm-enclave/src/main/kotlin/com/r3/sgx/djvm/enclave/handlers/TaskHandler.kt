package com.r3.sgx.djvm.enclave.handlers

import com.r3.sgx.djvm.enclave.connections.DJVMConnection
import com.r3.sgx.djvm.enclave.internal.DJVMBase
import com.r3.sgx.djvm.enclave.internal.SandboxRunner
import com.r3.sgx.djvm.enclave.messages.MessageType
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
import java.util.function.Consumer

/**
 * @userJars Contains the user code to be setup and loaded in the DJVM.
 */
class TaskHandler {

    private val bootstrapJar: MemoryURL
    private val parentConfiguration: SandboxConfiguration
    init {
        val bootstrapJarData = javaClass.classLoader.getResourceAsStream("deterministic-rt.zip").readBytes()
        bootstrapJar = JarHandler.createMemoryURL(ByteBuffer.wrap(bootstrapJarData))
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

    fun onReceive(connection: DJVMConnection, input: ByteBuffer) {
        val djvmMemoryClassLoader = DJVMBootstrapClassLoader(listOf(bootstrapJar))
        val userSource = DJVMUserClassSource(connection.userJars)
        DJVMBase.setupClassLoader(userSource, djvmMemoryClassLoader, emptyList(), parentConfiguration)

        val classNameSize = input.int
        val className = ByteArray(classNameSize)
        input.get(className)

        val inputSize = input.int
        val taskInput = ByteArray(inputSize)
        input.get(taskInput)

        val result : Any? =
            try {
                SandboxRunner().run(String(className), String(taskInput))
            } catch (exception: Throwable) {
                exception.toString()
            }
        DJVMBase.destroyRootContext()

        connection.send(2 * Int.SIZE_BYTES + classNameSize
                + Int.SIZE_BYTES + result.toString().toByteArray().size, Consumer { buffer ->
            buffer.putInt(MessageType.TASK.ordinal)
            buffer.putInt(classNameSize)
            buffer.put(className)
            buffer.putInt(result.toString().toByteArray().size)
            buffer.put(result.toString().toByteArray())
        })
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
