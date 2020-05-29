package com.r3.conclave.jvmtester.enclave.sandbox

import com.r3.conclave.common.SHA256Hash
import com.r3.conclave.common.internal.readFully
import com.r3.conclave.jvmtester.djvm.testutils.DJVMBase
import com.r3.conclave.jvmtester.djvm.testutils.createSandboxConfiguration
import com.r3.conclave.jvmtester.enclave.TestEnvironment
import com.r3.conclave.utils.classloaders.MemoryURL
import net.corda.djvm.SandboxConfiguration
import net.corda.djvm.analysis.AnalysisConfiguration
import net.corda.djvm.source.UserPathSource
import java.nio.ByteBuffer

/**
 * Abstract environment which sets up the sandbox but does not specify how the test should be run.
 */
abstract class AbstractSandboxEnvironment : TestEnvironment {
    companion object {
        val bootstrapJar: MemoryURL
        val parentConfiguration: SandboxConfiguration
        init {
            val bootstrapJarData = AbstractSandboxEnvironment::class.java.classLoader
                    .getResourceAsStream("deterministic-rt.zip")!!
                    .readFully()
            bootstrapJar = DJVMMemoryURLStreamHandler.createURL(
                    SHA256Hash.hash(bootstrapJarData).toString(),
                    ByteBuffer.wrap(bootstrapJarData)
            )
            val djvmMemoryClassLoader = DJVMApiSourceClassLoader(listOf(bootstrapJar))
            val rootConfiguration = AnalysisConfiguration.createRoot(
                    userSource = UserPathSource(emptyList()),
                    whitelist = DJVMBase.TEST_WHITELIST,
                    bootstrapSource = djvmMemoryClassLoader
            )
            parentConfiguration = createSandboxConfiguration(rootConfiguration)
        }
    }

    override fun setup(userJars: List<MemoryURL>) {
        val djvmMemoryClassLoader = DJVMApiSourceClassLoader(listOf(bootstrapJar))
        val userSource = DJVMUserSourceClassLoader(userJars)
        DJVMBase.setupClassLoader(userSource, djvmMemoryClassLoader, emptyList(), parentConfiguration)
    }

    override fun destroy() {
        DJVMBase.destroyRootContext()
    }
}
