package com.r3.conclave.testing.internal

import com.r3.conclave.common.OpaqueBytes
import com.r3.conclave.common.internal.*
import com.r3.conclave.enclave.Enclave
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatExceptionOfType
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import java.security.GeneralSecurityException
import kotlin.random.Random

class MockEnclaveEnvironmentTest {
    private companion object {
        private val plaintext = OpaqueBytes("Super Secret!".toByteArray())
        private val authenticatedData = OpaqueBytes("Authenticate!".toByteArray())
    }

    class EnclaveA : Enclave()

    class EnclaveB : Enclave()

    @Test
    fun createReport() {
        val env1 = createMockEnclaveEnvironment<EnclaveA>(isvProdId = 4, isvSvn = 3)
        val reportData = Random.nextBytes(SgxReportData.size)
        val report1 = Cursor.allocate(SgxReport)
        env1.createReport(null, reportData, report1.buffer.array())
        with(report1[SgxReport.body]) {
            assertThat(this[SgxReportBody.attributes][SgxAttributes.flags].isSet(SgxEnclaveFlags.DEBUG)).isTrue()
            assertThat(this[SgxReportBody.isvProdId].read()).isEqualTo(4)
            assertThat(this[SgxReportBody.isvSvn].read()).isEqualTo(3)
            assertThat(this[SgxReportBody.reportData].bytes).isEqualTo(reportData)
        }

        val env2 = createMockEnclaveEnvironment<EnclaveB>(isvProdId = 5, isvSvn = 6)
        val report2 = Cursor.allocate(SgxReport)
        env2.createReport(null, reportData, report2.buffer.array())
        assertThat(report2[SgxReport.body][SgxReportBody.cpuSvn]).isEqualTo(report1[SgxReport.body][SgxReportBody.cpuSvn])
        assertThat(report2[SgxReport.body][SgxReportBody.mrenclave]).isNotEqualTo(report1[SgxReport.body][SgxReportBody.mrenclave])
    }

    @ParameterizedTest(name = "{displayName} {argumentsWithNames}")
    @ValueSource(booleans = [true, false])
    fun `seal and unseal on same enclave instance`(useAuthenticatedData: Boolean) {
        val env = createMockEnclaveEnvironment<EnclaveA>()
        val input = PlaintextAndEnvelope(plaintext, authenticatedData.takeIf { useAuthenticatedData })
        val sealedBlob = env.sealData(input)
        assertThat(env.unsealData(sealedBlob)).isEqualTo(input)
    }

    @ParameterizedTest(name = "{displayName} {argumentsWithNames}")
    @ValueSource(booleans = [true, false])
    fun `seal and unseal on different instance of same enclave`(useAuthenticatedData: Boolean) {
        val env1 = createMockEnclaveEnvironment<EnclaveA>()
        val env2 = createMockEnclaveEnvironment<EnclaveA>()
        val input = PlaintextAndEnvelope(plaintext, authenticatedData.takeIf { useAuthenticatedData })
        val sealedBlob = env1.sealData(input)
        assertThat(env2.unsealData(sealedBlob)).isEqualTo(input)
    }

    @ParameterizedTest(name = "{displayName} {argumentsWithNames}")
    @ValueSource(booleans = [true, false])
    fun `seal and unseal on different enclaves`(useAuthenticatedData: Boolean) {
        val envA = createMockEnclaveEnvironment<EnclaveA>()
        val envB = createMockEnclaveEnvironment<EnclaveB>()
        val input = PlaintextAndEnvelope(plaintext, authenticatedData.takeIf { useAuthenticatedData })
        val sealedBlob = envA.sealData(input)
        assertThatExceptionOfType(GeneralSecurityException::class.java).isThrownBy {
            envB.unsealData(sealedBlob)
        }
    }

    @Test
    fun `sealed data is different on same plaintext`() {
        val env = createMockEnclaveEnvironment<EnclaveA>()
        val sealedBlob1 = env.sealData(PlaintextAndEnvelope(plaintext))
        val sealedBlob2 = env.sealData(PlaintextAndEnvelope(plaintext))
        assertThat(sealedBlob1).isNotEqualTo(sealedBlob2)
    }

    private inline fun <reified E : Enclave> createMockEnclaveEnvironment(
            isvProdId: Int = 1,
            isvSvn: Int = 1
    ): MockEnclaveEnvironment {
        return MockEnclaveEnvironment(E::class.java.getConstructor().newInstance(), isvProdId, isvSvn)
    }
}
