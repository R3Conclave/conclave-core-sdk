package com.r3.conclave.enclave.internal

import com.r3.conclave.common.MockCallInterfaceConnector
import com.r3.conclave.common.MockConfiguration
import com.r3.conclave.common.internal.*
import com.r3.conclave.enclave.Enclave
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatExceptionOfType
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import java.nio.ByteBuffer
import java.security.GeneralSecurityException
import kotlin.random.Random

class MockEnclaveEnvironmentTest {
    private companion object {
        private val plaintext = "Super Secret!".toByteArray()
        private val authenticatedData = "Authenticate!".toByteArray()
    }

    class EnclaveA : Enclave()

    class EnclaveB : Enclave()

    @Test
    fun createReport() {
        val mockConfig = MockConfiguration()
        mockConfig.productID = 4
        mockConfig.revocationLevel = 3
        val env1 = createMockEnclaveEnvironment<EnclaveA>(mockConfig)
        val reportData = Random.nextBytes(SgxReportData.size)
        val reportBody1 = env1.createReport(null, Cursor.wrap(SgxReportData, reportData))[SgxReport.body]
        assertThat(reportBody1[SgxReportBody.attributes][SgxAttributes.flags].isSet(SgxEnclaveFlags.DEBUG)).isTrue
        assertThat(reportBody1[SgxReportBody.isvProdId].read()).isEqualTo(4)
        // isvSvn is 1 based whereas revocationLevel is zero based.
        assertThat(reportBody1[SgxReportBody.isvSvn].read()).isEqualTo(4)
        assertThat(reportBody1[SgxReportBody.reportData].bytes).isEqualTo(reportData)

        mockConfig.productID = 5
        mockConfig.revocationLevel = 6
        val env2 = createMockEnclaveEnvironment<EnclaveB>(mockConfig)
        val reportBody2 = env2.createReport(null, null)[SgxReport.body]
        assertThat(reportBody2[SgxReportBody.cpuSvn]).isEqualTo(reportBody1[SgxReportBody.cpuSvn])
        assertThat(reportBody2[SgxReportBody.mrenclave]).isNotEqualTo(reportBody1[SgxReportBody.mrenclave])
    }

    @ParameterizedTest(name = "{displayName} {argumentsWithNames}")
    @ValueSource(booleans = [true, false])
    fun `seal and unseal on same enclave instance`(useAuthenticatedData: Boolean) {
        val env = createMockEnclaveEnvironment<EnclaveA>()
        val input = PlaintextAndEnvelope(plaintext, authenticatedData.takeIf { useAuthenticatedData })
        val sealedBlob = env.sealData(input)
        assertThat(env.unsealData(ByteBuffer.wrap(sealedBlob))).isEqualTo(input)
    }

    @ParameterizedTest(name = "{displayName} {argumentsWithNames}")
    @ValueSource(booleans = [true, false])
    fun `seal and unseal on different instance of same enclave`(useAuthenticatedData: Boolean) {
        val env1 = createMockEnclaveEnvironment<EnclaveA>()
        val env2 = createMockEnclaveEnvironment<EnclaveA>()
        val input = PlaintextAndEnvelope(plaintext, authenticatedData.takeIf { useAuthenticatedData })
        val sealedBlob = env1.sealData(input)
        assertThat(env2.unsealData(ByteBuffer.wrap(sealedBlob))).isEqualTo(input)
    }

    @ParameterizedTest(name = "{displayName} {argumentsWithNames}")
    @ValueSource(booleans = [true, false])
    fun `seal and unseal on different enclaves`(useAuthenticatedData: Boolean) {
        val envA = createMockEnclaveEnvironment<EnclaveA>()
        val envB = createMockEnclaveEnvironment<EnclaveB>()
        val input = PlaintextAndEnvelope(plaintext, authenticatedData.takeIf { useAuthenticatedData })
        val sealedBlob = envA.sealData(input)
        assertThatExceptionOfType(GeneralSecurityException::class.java).isThrownBy {
            envB.unsealData(ByteBuffer.wrap(sealedBlob))
        }
    }

    @Test
    fun `sealed data is different on same plaintext`() {
        val env = createMockEnclaveEnvironment<EnclaveA>()
        val sealedBlob1 = env.sealData(PlaintextAndEnvelope(plaintext))
        val sealedBlob2 = env.sealData(PlaintextAndEnvelope(plaintext))
        assertThat(sealedBlob1).isNotEqualTo(sealedBlob2)
    }

    @Test
    fun `mock platform update and downgrade reflected in createReport`() {
        var env = createMockEnclaveEnvironment<EnclaveA>()
        val cpuSvnBeforeUpdate = env.createReport(null, null).cpuSvn
        val mockConfig = MockConfiguration()
        mockConfig.tcbLevel = 2
        env = createMockEnclaveEnvironment<EnclaveA>(mockConfig)
        val cpuSvnAfterUpdate = env.createReport(null, null).cpuSvn
        assertThat(cpuSvnBeforeUpdate).isNotEqualTo(cpuSvnAfterUpdate)
        mockConfig.tcbLevel = 1
        env = createMockEnclaveEnvironment<EnclaveA>(mockConfig)
        val cpuSvnAfterDowngrade = env.createReport(null, null).cpuSvn
        assertThat(cpuSvnBeforeUpdate).isEqualTo(cpuSvnAfterDowngrade)
    }

    @Test
    fun `getSecretKey able to generate key for previous CPUSVN`() {
        var env = createMockEnclaveEnvironment<EnclaveA>()
        val cpuSvnBeforeUpdate = env.createReport(null, null).cpuSvn
        val secretKeyBeforeUpdate = env.getSecretKey { keyRequest ->
            keyRequest[SgxKeyRequest.keyName] = KeyName.SEAL
            keyRequest[SgxKeyRequest.cpuSvn] = cpuSvnBeforeUpdate.buffer
        }
        val mockConfig = MockConfiguration()
        mockConfig.tcbLevel = 2
        env = createMockEnclaveEnvironment<EnclaveA>(mockConfig)
        val secretKeyDerivedFromPreviousCpuSvn = env.getSecretKey { keyRequest ->
            keyRequest[SgxKeyRequest.keyName] = KeyName.SEAL
            keyRequest[SgxKeyRequest.cpuSvn] = cpuSvnBeforeUpdate.buffer
        }
        assertThat(secretKeyDerivedFromPreviousCpuSvn).isEqualTo(secretKeyBeforeUpdate)
    }

    private val ByteCursor<SgxReport>.cpuSvn: ByteCursor<SgxCpuSvn> get() = this[SgxReport.body][SgxReportBody.cpuSvn]

    private inline fun <reified E : Enclave> createMockEnclaveEnvironment(
            mockConfiguration: MockConfiguration? = null
    ): MockEnclaveEnvironment {
        return MockEnclaveEnvironment(E::class.java.getConstructor().newInstance(), mockConfiguration, null, MockCallInterfaceConnector())
    }
}
