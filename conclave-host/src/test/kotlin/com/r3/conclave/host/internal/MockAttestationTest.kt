package com.r3.conclave.host.internal

import com.r3.conclave.common.EnclaveInstanceInfo
import com.r3.conclave.common.EnclaveMode
import com.r3.conclave.common.EnclaveSecurityInfo
import com.r3.conclave.common.internal.EnclaveInstanceInfoImpl
import com.r3.conclave.common.internal.attestation.DcapAttestation
import com.r3.conclave.common.internal.attestation.MockAttestation
import com.r3.conclave.enclave.Enclave
import com.r3.conclave.host.EnclaveHost
import com.r3.conclave.host.EnclaveHostMockTest
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatIllegalArgumentException
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assumptions.assumeFalse
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.lang.Thread.sleep

class MockAttestationTest {
    private val enclaveHost: EnclaveHost = createMockHost(EnclaveInstanceInfoEnclave::class.java)

    class EnclaveInstanceInfoEnclave : Enclave() {
        override fun receiveFromUntrustedHost(bytes: ByteArray): ByteArray = enclaveInstanceInfo.serialize()
    }

    class SignedQuoteEnclave : Enclave() {
        override fun receiveFromUntrustedHost(bytes: ByteArray): ByteArray = createAttestationQuote(bytes)
    }

    @BeforeEach
    fun start() {
        enclaveHost.start(null, null, null) { }
        assertThat(enclaveHost.enclaveMode).isEqualTo(EnclaveMode.MOCK)
        assertThat((enclaveHost.enclaveInstanceInfo as EnclaveInstanceInfoImpl).attestation).isInstanceOf(
            MockAttestation::class.java
        )
    }

    @AfterEach
    fun cleanUp() {
        enclaveHost.close()
    }

    @Test
    fun `mock is insecure`() {
        assertThat(enclaveHost.enclaveInstanceInfo.securityInfo.summary).isEqualTo(EnclaveSecurityInfo.Summary.INSECURE)
    }

    @Test
    fun `EnclaveInstanceInfo serialisation round-trip`() {
        val roundTrip = EnclaveInstanceInfo.deserialize(enclaveHost.enclaveInstanceInfo.serialize())
        assertThat(roundTrip).isEqualTo(enclaveHost.enclaveInstanceInfo)
    }

    @Test
    fun `EnclaveInstanceInfo deserialize throws IllegalArgumentException on truncated bytes`() {
        val serialised = enclaveHost.enclaveInstanceInfo.serialize()
        for (truncatedSize in serialised.indices) {
            val truncated = serialised.copyOf(truncatedSize)
            val thrownBy =
                assertThatIllegalArgumentException().describedAs("Truncated size $truncatedSize").isThrownBy {
                    EnclaveInstanceInfo.deserialize(truncated)
                }
            if (truncatedSize > 3) {
                thrownBy.withMessage("Truncated EnclaveInstanceInfo bytes")
            } else {
                thrownBy.withMessage("Not EnclaveInstanceInfo bytes")
            }
        }
    }

    @Test
    fun `EnclaveInstanceInfo matches across host and enclave`() {
        assumeFalse((enclaveHost.enclaveInstanceInfo as EnclaveInstanceInfoImpl).attestation is DcapAttestation)
        val eiiFromEnclave = EnclaveInstanceInfo.deserialize(enclaveHost.callEnclave(byteArrayOf())!!)
        val eiiFromHost = enclaveHost.enclaveInstanceInfo
        assertThat(eiiFromEnclave).isEqualTo(eiiFromHost)
    }

    @Test
    fun `ensure the attestation can be run multiple times`() {
        val host = createMockHost(EnclaveHostMockTest.EnclaveWithHooks::class.java)
        host.start(null, null, null) { }
        val onStartupAttestationTimestamp = host.enclaveInstanceInfo.securityInfo.timestamp

        // The timestamp resolution from Java 8 is not high enough to detect that time has passed while the test executed
        // Although adding sleeps to tests is not a good practice that is the only solution for now.
        // The sleep call can be removed as soon as Java 8 stops being supported
        sleep(2)
        host.updateAttestation()
        val attestationTimestampRun1 = host.enclaveInstanceInfo.securityInfo.timestamp
        assertThat(attestationTimestampRun1).isAfter(onStartupAttestationTimestamp)

        // The timestamp resolution from Java 8 is not high enough to detect that time has passed while the test executed
        // Although adding sleeps to tests is not a good practice that is the only solution for now.
        // The sleep call can be removed as soon as Java 8 stops being supported
        sleep(2)
        host.updateAttestation()
        val attestationTimestampRun2 = host.enclaveInstanceInfo.securityInfo.timestamp
        assertThat(attestationTimestampRun2).isAfter(attestationTimestampRun1)
    }

    @Test
    fun `ensure an exception is raised by createAttestationQuote if the report data does not have the correct size`() {
        createMockHost(SignedQuoteEnclave::class.java).use { host ->
            host.start(null, null, null) { }
            val emptyReportData = byteArrayOf()
            val exception = assertThrows<IllegalArgumentException> { host.callEnclave(emptyReportData) }
            assertThat(exception.message).isEqualTo("User report data must be 64 bytes long, but was 0 bytes instead.")
        }
    }
}
