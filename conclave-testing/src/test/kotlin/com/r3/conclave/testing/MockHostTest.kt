package com.r3.conclave.testing

import com.r3.conclave.common.EnclaveMode
import com.r3.conclave.common.EnclaveCall
import com.r3.conclave.enclave.Enclave
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class MockHostTest {
    @Test
    fun `simple sample test, with assesses to enclave internals`() {
        val mock = MockHost.loadMock<PreviousValueEnclave>()
        mock.start(null, null, null)

        assertThat(mock.enclave.previousValue).isNull()
        val firstResponse = mock.callEnclave("Hello".toByteArray())
        assertThat(firstResponse).isNull()

        assertThat(mock.enclave.previousValue).isEqualTo("Hello".toByteArray())
        val secondResponse = mock.callEnclave("World".toByteArray())
        assertThat(secondResponse).isEqualTo("Hello".toByteArray())
        assertThat(mock.enclave.previousValue).isEqualTo("World".toByteArray())
    }

    @Test
    fun `enclaveInfo values`() {
        val mock = MockHost.loadMock<PreviousValueEnclave>()
        mock.start(null, null, null)
        assertThat(mock.enclaveInstanceInfo.enclaveInfo.enclaveMode).isEqualTo(EnclaveMode.MOCK)
        assertThat(mock.enclaveInstanceInfo.enclaveInfo.codeHash.bytes).containsOnly(0)
        assertThat(mock.enclaveInstanceInfo.enclaveInfo.codeSigningKeyHash.bytes).containsOnly(0)
    }

    class PreviousValueEnclave : EnclaveCall, Enclave() {
        var previousValue: ByteArray? = null

        override fun invoke(bytes: ByteArray): ByteArray? {
            val previousValue = this.previousValue
            this.previousValue = bytes
            return previousValue
        }
    }
}
