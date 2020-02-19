package com.r3.sgx.test.enclave.handlers

import com.r3.sgx.djvm.DJVMBase
import com.r3.sgx.test.enclave.TestEnclave
import com.r3.sgx.test.enclave.djvm.classloaders.DJVMApiSourceClassLoader
import com.r3.sgx.test.enclave.djvm.classloaders.DJVMUserSourceClassLoader
import com.r3.sgx.test.enclave.djvm.url.DJVMMemoryURLStreamHandler
import com.r3.sgx.test.enclave.hashKey
import com.r3.sgx.test.enclave.messages.MessageType
import com.r3.sgx.test.enclave.sha256
import com.r3.sgx.utils.classloaders.MemoryURL
import net.corda.djvm.SandboxConfiguration
import net.corda.djvm.analysis.AnalysisConfiguration
import net.corda.djvm.execution.ExecutionProfile
import net.corda.djvm.source.UserPathSource
import java.nio.ByteBuffer

/**
 * Handler to run test classes in the enclave, in a DJVM sandbox
 */
open class SandboxTestHandler : TestHandler {

    companion object {
        val bootstrapJar: MemoryURL
        val parentConfiguration: SandboxConfiguration
        init {
            val bootstrapJarData = SandboxTestHandler::class.java.classLoader.getResourceAsStream("deterministic-rt.zip").readBytes()
            bootstrapJar = ByteBuffer.wrap(bootstrapJarData).let {
                DJVMMemoryURLStreamHandler.createURL(it.sha256.hashKey, it)
            }
            val djvmMemoryClassLoader = DJVMApiSourceClassLoader(listOf(bootstrapJar))
            val rootConfiguration = AnalysisConfiguration.createRoot(
                    userSource = UserPathSource(emptyList()),
                    whitelist = DJVMBase.TEST_WHITELIST,
                    bootstrapSource = djvmMemoryClassLoader
            )
            parentConfiguration = createSandboxConfiguration(rootConfiguration)
        }

        @JvmStatic
        fun createSandboxConfiguration(analysisConfiguration: AnalysisConfiguration): SandboxConfiguration {
            return SandboxConfiguration.of(
                    ExecutionProfile.UNLIMITED,
                    SandboxConfiguration.ALL_RULES,
                    SandboxConfiguration.ALL_EMITTERS,
                    SandboxConfiguration.ALL_DEFINITION_PROVIDERS,
                    analysisConfiguration
            )
        }
    }

    override fun setup(connection: TestEnclave.EnclaveConnection) {
        val djvmMemoryClassLoader = DJVMApiSourceClassLoader(listOf(bootstrapJar))
        val userSource = DJVMUserSourceClassLoader(connection.userJars)

        DJVMBase.setupClassLoader(userSource, djvmMemoryClassLoader, emptyList(), parentConfiguration)
    }

    override fun messageType(): MessageType = MessageType.SANDBOX_TEST
}