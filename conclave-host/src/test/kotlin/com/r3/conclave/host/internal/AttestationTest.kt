package com.r3.conclave.host.internal

import com.r3.conclave.common.EnclaveInstanceInfo
import com.r3.conclave.common.EnclaveMode
import com.r3.conclave.common.EnclaveSecurityInfo
import com.r3.conclave.common.internal.EnclaveInstanceInfoImpl
import com.r3.conclave.common.internal.attestation.Attestation
import com.r3.conclave.common.internal.attestation.DcapAttestation
import com.r3.conclave.common.internal.attestation.EpidAttestation
import com.r3.conclave.common.internal.attestation.MockAttestation
import com.r3.conclave.enclave.Enclave
import com.r3.conclave.host.AttestationParameters
import com.r3.conclave.host.EnclaveHost
import com.r3.conclave.internaltesting.HardwareTest
import com.r3.conclave.internaltesting.dynamic.EnclaveBuilder
import com.r3.conclave.internaltesting.dynamic.EnclaveType
import com.r3.conclave.internaltesting.dynamic.TestEnclaves
import com.r3.conclave.testing.MockHost
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatIllegalArgumentException
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assumptions.assumeFalse
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension

abstract class AttestationTest {
    abstract val attestationParameters: AttestationParameters?
    abstract val expectedEnclaveMode: EnclaveMode
    abstract val expectedAttestationType: Class<out Attestation>
    abstract val enclaveHost: EnclaveHost

    class EnclaveInstanceInfoEnclave : Enclave() {
        override fun receiveFromUntrustedHost(bytes: ByteArray): ByteArray? = enclaveInstanceInfo.serialize()
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
        // TODO Don't run this test with DCAP as ECDSA sig verification doesn't work with Avian. We don't plan to fix
        //  this but rather we should make sure it works with Graal. However that requires changes to TestEnclaves as it
        //  only produces Avian enclaves. https://r3-cev.atlassian.net/browse/CON-248.
        assumeFalse((enclaveHost.enclaveInstanceInfo as EnclaveInstanceInfoImpl).attestation is DcapAttestation)
        val eiiFromEnclave = EnclaveInstanceInfo.deserialize(enclaveHost.callEnclave(byteArrayOf())!!)
        val eiiFromHost = enclaveHost.enclaveInstanceInfo
        assertThat(eiiFromEnclave).isEqualTo(eiiFromHost)
    }
}

class HardwareAttestationTest : HardwareTest, AttestationTest() {
    companion object {
        @JvmField
        @RegisterExtension
        val testEnclaves = TestEnclaves()
    }

    override val attestationParameters: AttestationParameters
        get() = HardwareTest.attestationParams

    override val expectedEnclaveMode: EnclaveMode
        get() = EnclaveMode.DEBUG

    override val expectedAttestationType: Class<out Attestation>
        get() = if (attestationParameters is AttestationParameters.EPID) EpidAttestation::class.java else DcapAttestation::class.java

    override val enclaveHost: EnclaveHost =
        testEnclaves.hostTo(EnclaveInstanceInfoEnclave::class.java, EnclaveBuilder(type = EnclaveType.Debug))
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

class SimulationModeAttestationTest : MockAttestationTest() {
    companion object {
        @JvmField
        @RegisterExtension
        val testEnclaves = TestEnclaves()
    }

    override val expectedEnclaveMode: EnclaveMode
        get() = EnclaveMode.SIMULATION

    override val enclaveHost: EnclaveHost = testEnclaves.hostTo(EnclaveInstanceInfoEnclave::class.java)
}

class MockModeAttestationTest : MockAttestationTest() {
    override val expectedEnclaveMode: EnclaveMode
        get() = EnclaveMode.MOCK

    override val enclaveHost: EnclaveHost = MockHost.loadMock<EnclaveInstanceInfoEnclave>()
}
