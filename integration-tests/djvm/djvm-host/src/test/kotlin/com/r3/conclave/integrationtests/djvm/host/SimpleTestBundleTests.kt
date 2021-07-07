package com.r3.conclave.integrationtests.djvm.host

import com.r3.conclave.common.OpaqueBytes
import com.r3.conclave.integrationtests.djvm.base.EnclaveJvmTest
import com.r3.conclave.integrationtests.djvm.base.enclave.proto.ExecuteTest
import com.r3.conclave.integrationtests.djvm.base.loadTestClasses
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.extension.ExtensionContext
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.ArgumentsProvider
import org.junit.jupiter.params.provider.ArgumentsSource
import java.nio.file.Paths
import java.util.stream.Stream

@Disabled
class SimpleTestBundleTests {
    companion object {
        private val simpleTestBundleJar = Paths.get(System.getProperty("simple-test-bundle-jar"))

        private val tests = loadTestClasses(listOf(simpleTestBundleJar))
        private val enclaveHost = DjvmEnclaveHost()

        @BeforeAll
        @JvmStatic
        fun start() {
            val spid = OpaqueBytes.parse(System.getProperty("conclave.spid"))
            val attestationKey = checkNotNull(System.getProperty("conclave.attestation-key"))
            enclaveHost.start(spid, attestationKey)
            enclaveHost.loadJar(simpleTestBundleJar)
        }

        @AfterAll
        @JvmStatic
        fun shutdown() {
            enclaveHost.close()
        }
    }

    class JvmTestArgumentsProvider : ArgumentsProvider {
        override fun provideArguments(context: ExtensionContext?): Stream<out Arguments> {
            return tests.stream().map { Arguments.of(it) }
        }
    }

    @ArgumentsSource(JvmTestArgumentsProvider::class)
    @ParameterizedTest(name = "{index} => {0}")
    fun `without any DJVM sandbox`(testClass: Class<out EnclaveJvmTest>) {
        val test = testClass.newInstance()
        val result = enclaveHost.executeTest(ExecuteTest.Mode.STANDARD, test)
        test.assertResult(result)
    }

    @ArgumentsSource(JvmTestArgumentsProvider::class)
    @ParameterizedTest(name = "{index} => {0}")
    fun `with DJVM sandbox`(testClass: Class<out EnclaveJvmTest>) {
        val test = testClass.newInstance()
        val result = enclaveHost.executeTest(ExecuteTest.Mode.SANDBOX, test)
        test.assertResult(result)
    }
}
