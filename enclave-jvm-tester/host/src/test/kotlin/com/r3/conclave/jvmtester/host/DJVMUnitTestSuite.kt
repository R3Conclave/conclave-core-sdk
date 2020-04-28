package com.r3.conclave.jvmtester.host

import com.r3.conclave.common.OpaqueBytes
import com.r3.conclave.jvmtester.api.EnclaveJvmTest
import com.r3.conclave.jvmtester.api.enclave.proto.ExecuteTest
import com.r3.conclave.jvmtester.djvm.testutils.loadTestClasses
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.extension.ExtensionContext
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.ArgumentsProvider
import org.junit.jupiter.params.provider.ArgumentsSource
import java.nio.file.Paths
import java.util.stream.Stream

class DJVMUnitTestSuite {
    companion object {
        /**
         * This jar represents the custom logic loaded from a user provider jar to be run in the DJVM sandbox.
         */
        private val userJarPath = Paths.get(System.getProperty("djvm-unit-tests-jar.path"))

        private val tests = loadTestClasses(listOf(userJarPath))
                .filter {
                    "SandboxObjectHashCodeJavaTest\$TestHashForNullObjectEnclaveTest" !in it.name // ENT-4705 SIGSEGV
                }

        private val enclaveHost = TesterEnclaveHost()

        @BeforeAll
        @JvmStatic
        fun start() {
            val spid = OpaqueBytes.parse(System.getProperty("conclave.spid"))
            val attestationKey = checkNotNull(System.getProperty("conclave.attestation-key"))
            enclaveHost.start(spid, attestationKey)
            enclaveHost.loadJar(userJarPath)
        }

        @AfterAll
        @JvmStatic
        fun shutdown() {
            enclaveHost.close()
        }
    }

    class DJVMTestArgumentProvider : ArgumentsProvider {
        override fun provideArguments(context: ExtensionContext): Stream<out Arguments> {
            return tests.stream().map { Arguments.of(it) }
        }
    }

    @ArgumentsSource(DJVMTestArgumentProvider::class)
    @ParameterizedTest(name = "{index} => {0}")
    fun runDJVMEnclaveTests(testClass: Class<out EnclaveJvmTest>) {
        val test = testClass.newInstance()
        val result = enclaveHost.executeTest(ExecuteTest.Mode.JUST_SETUP_SANDBOX, test)
        test.assertResult(result)
    }
}
