package com.r3.conclave.integrationtests.general.tests

import com.r3.conclave.common.EnclaveInstanceInfo
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatIllegalArgumentException
import org.junit.jupiter.api.Test

class AttestationTests : JvmTest("com.r3.conclave.integrationtests.general.enclave.NonThreadSafeEnclaveWithPostOffice") {

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
            val thrownBy = assertThatIllegalArgumentException().describedAs("Truncated size $truncatedSize")
                                .isThrownBy { EnclaveInstanceInfo.deserialize(truncated) }

            if (truncatedSize > 3) {
                    thrownBy.withMessage("Truncated EnclaveInstanceInfo bytes")
            } else {
                    thrownBy.withMessage("Not EnclaveInstanceInfo bytes")
            }
        }
    }

    @Test
    fun `EnclaveInstanceInfo matches across host and enclave`() {
        val eiiFromHost = enclaveHost.enclaveInstanceInfo
        val eiiFromEnclave = EnclaveInstanceInfo.deserialize(enclaveHost.callEnclave("EnclaveInstanceInfo.serialize()".toByteArray())!!)
        assertThat(eiiFromEnclave).isEqualTo(eiiFromHost)
    }
}
