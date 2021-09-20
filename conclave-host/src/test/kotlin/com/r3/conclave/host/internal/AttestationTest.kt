package com.r3.conclave.host.internal

import com.r3.conclave.common.EnclaveInstanceInfo
import com.r3.conclave.common.EnclaveMode
import com.r3.conclave.common.EnclaveSecurityInfo
import com.r3.conclave.common.internal.EnclaveInstanceInfoImpl
import com.r3.conclave.common.internal.attestation.Attestation
import com.r3.conclave.common.internal.attestation.DcapAttestation
import com.r3.conclave.common.internal.attestation.MockAttestation
import com.r3.conclave.enclave.Enclave
import com.r3.conclave.host.AttestationParameters
import com.r3.conclave.host.EnclaveHost
import com.r3.conclave.host.EnclaveHostMockTest
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatIllegalArgumentException
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assumptions.assumeFalse
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.lang.Thread.sleep

// TODO exact copies of these tests are used for DCAP in Simulation and Debug modes
//  at integration-tests/general/tests/src/test/kotlin/com/r3/conclave/integrationtests/general/tests/AttestationTests.kt .
//  We want to eventually get rid of this duplication.
abstract class AttestationTest {
    abstract val attestationParameters: AttestationParameters?
    abstract val expectedEnclaveMode: EnclaveMode
    abstract val expectedAttestationType: Class<out Attestation>
    abstract val enclaveHost: EnclaveHost

    class EnclaveInstanceInfoEnclave : Enclave() {
        override fun receiveFromUntrustedHost(bytes: ByteArray): ByteArray = enclaveInstanceInfo.serialize()
    }

    @BeforeEach
    fun start() {
        enclaveHost.start(attestationParameters, null)
        assertThat(enclaveHost.enclaveMode).isEqualTo(expectedEnclaveMode)
        assertThat((enclaveHost.enclaveInstanceInfo as EnclaveInstanceInfoImpl).attestation).isInstanceOf(
            expectedAttestationType
        )
    }

    @AfterEach
    fun cleanUp() {
        enclaveHost.close()
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
        host.start(null, null)
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
}

abstract class MockAttestationTest : AttestationTest() {
    override val attestationParameters: AttestationParameters?
        get() = null

    override val expectedAttestationType: Class<out Attestation>
        get() = MockAttestation::class.java

    @AfterEach
    fun `mock is always insecure`() {
        assertThat(enclaveHost.enclaveInstanceInfo.securityInfo.summary).isEqualTo(EnclaveSecurityInfo.Summary.INSECURE)
    }
}

class MockModeAttestationTest : MockAttestationTest() {
    override val expectedEnclaveMode: EnclaveMode
        get() = EnclaveMode.MOCK

    override val enclaveHost: EnclaveHost = createMockHost(EnclaveInstanceInfoEnclave::class.java)
}
