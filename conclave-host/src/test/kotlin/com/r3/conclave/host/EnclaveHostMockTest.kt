package com.r3.conclave.host

import com.r3.conclave.common.enclave.EnclaveCall
import com.r3.conclave.enclave.Enclave
import com.r3.conclave.enclave.kotlin.callUntrustedHost
import com.r3.conclave.host.kotlin.callEnclave
import com.r3.conclave.testing.MockHost
import com.r3.conclave.testing.RecordingEnclaveCall
import org.assertj.core.api.Assertions.*
import org.junit.jupiter.api.Test

class EnclaveHostMockTest {
    @Test
    fun `enclaveInstanceInfo before start`() {
        val host = MockHost.loadMock<SimpleReturnEnclave>()
        assertThatIllegalStateException().isThrownBy {
            host.enclaveInstanceInfo
        }.withMessage("Enclave has not been started.")
    }

    @Test
    fun `calling into enclave which doesn't implement EnclaveCall`() {
        val host = MockHost.loadMock<EnclaveWithoutEnclaveCall>()
        host.start(null, null)
        assertThatIllegalStateException().isThrownBy {
            host.callEnclave(byteArrayOf())
        }.withMessage("The enclave does not implement EnclaveCall to receive messages from the host.")
        assertThatIllegalStateException().isThrownBy {
            host.callEnclave(byteArrayOf()) { it }
        }.withMessage("The enclave does not implement EnclaveCall to receive messages from the host.")
    }

    class EnclaveWithoutEnclaveCall : Enclave()

    @Test
    fun `enclave doesn't response to host message`() {
        val host = MockHost.loadMock<NoOpEnclave>()
        host.start(null, null)
        val (response, callbacks) = host.recordCallbacksFromEnclave("abcd".toByteArray())
        assertThat(response).isNull()
        assertThat(callbacks).isEmpty()
    }

    class NoOpEnclave : EnclaveCall, Enclave() {
        override fun invoke(bytes: ByteArray): ByteArray? = null
    }

    @Test
    fun `enclave response via invoke return (ie no callback)`() {
        val host = MockHost.loadMock<SimpleReturnEnclave>()
        host.start(null, null)
        val response = host.callEnclave(byteArrayOf(1))
        assertThat(response).isEqualTo(byteArrayOf(1, 2))
    }

    class SimpleReturnEnclave : EnclaveCall, Enclave() {
        override fun invoke(bytes: ByteArray): ByteArray? = bytes + 2
    }

    @Test
    fun `enclave response via callUntrustedHost`() {
        val host = MockHost.loadMock<SimpleCallbackEnclave>()
        host.start(null, null)
        val (response, callbacks) = host.recordCallbacksFromEnclave(byteArrayOf(1))
        assertThat(response).isNull()
        assertThat(callbacks).containsOnly(byteArrayOf(1, 2))
    }

    @Test
    fun `enclave response via callUntrustedHost but host has no callback`() {
        val host = MockHost.loadMock<SimpleCallbackEnclave>()
        host.start(null, null)
        assertThatIllegalArgumentException().isThrownBy {
            host.callEnclave("abcd".toByteArray())
        }.withMessage("Enclave responded via callUntrustedHost but a callback was not provided to callEnclave.")
    }

    class SimpleCallbackEnclave : EnclaveCall, Enclave() {
        override fun invoke(bytes: ByteArray): ByteArray? {
            callUntrustedHost(bytes + 2)
            return null
        }
    }

    @Test
    fun `enclave response via multiple callUntrustedHost and invoke return`() {
        val host = MockHost.loadMock<CallbacksAndReturnEnclave>()
        host.start(null, null)
        val (response, callbacks) = host.recordCallbacksFromEnclave(byteArrayOf(1))
        assertThat(response).isEqualTo(byteArrayOf(1, 4))
        assertThat(callbacks).containsExactly(byteArrayOf(1, 2), byteArrayOf(1, 3))
    }

    class CallbacksAndReturnEnclave : EnclaveCall, Enclave() {
        override fun invoke(bytes: ByteArray): ByteArray? {
            callUntrustedHost(bytes + 2)
            callUntrustedHost(bytes + 3)
            return bytes + 4
        }
    }

    @Test
    fun `back and forth between host and enclave (via the host returning from its callback)`() {
        val host = MockHost.loadMock<SimpleBackAndForthEnclave>()
        host.start(null, null)
        val response = host.callEnclave(byteArrayOf(1)) { fromEnclave -> fromEnclave + 3 }
        assertThat(response).isEqualTo(byteArrayOf(1, 2, 3, 4, 3, 5))
    }

    class SimpleBackAndForthEnclave : EnclaveCall, Enclave() {
        override fun invoke(bytes: ByteArray): ByteArray? {
            val response1 = callUntrustedHost(bytes + 2)!!
            assertThat(response1).isEqualTo(byteArrayOf(1, 2, 3))
            val response2 = callUntrustedHost(response1 + 4)!!
            assertThat(response2).isEqualTo(byteArrayOf(1, 2, 3, 4, 3))
            return response2 + 5
        }
    }

    @Test
    fun `host calls back into the enclave from its own callback`() {
        val host = MockHost.loadMock<NestedCallbackEnclave>()
        host.start(null, null)
        val response = host.callEnclave(byteArrayOf(1)) { fromEnclave ->
            host.callEnclave(fromEnclave + 3)!! + 5
        }
        assertThat(response).isEqualTo(byteArrayOf(1, 2, 3, 4, 5, 6))
    }

    class NestedCallbackEnclave : EnclaveCall, Enclave() {
        override fun invoke(bytes: ByteArray): ByteArray? {
            return callUntrustedHost(bytes + 2) { fromHost -> fromHost + 4 }!! + 6
        }
    }

    @Test
    fun `host calls back into the enclave but enclave has no callback`() {
        val host = MockHost.loadMock<SimpleCallbackEnclave>()
        host.start(null, null)
        assertThatIllegalArgumentException().isThrownBy {
            host.callEnclave(byteArrayOf(1)) { fromEnclave -> host.callEnclave(fromEnclave + 3) }
        }.withMessage("The enclave has not provided a callback to callUntrustedHost to receive the host's call back in.")
    }

    @Test
    fun `host calls back into the enclave with a 2nd layer callback, which gets invoked twice`() {
        val host = MockHost.loadMock<ComplexCallbackEnclave>()
        host.start(null, null)
        val response = host.callEnclave(byteArrayOf(1)) { first ->
            host.callEnclave(first + 3) { second -> second + 5 }!! + 8
        }
        assertThat(response).isEqualTo(byteArrayOf(1, 2, 3, 4, 5, 6, 5, 7, 8, 9))
    }

    class ComplexCallbackEnclave : EnclaveCall, Enclave() {
        override fun invoke(bytes: ByteArray): ByteArray? {
            return callUntrustedHost(bytes + 2) { fromHost ->
                val response1 = callUntrustedHost(fromHost + 4)!! + 6
                val response2 = callUntrustedHost(response1)!!
                response2 + 7
            }!! + 9
        }
    }

    @Test
    fun `host calls back into the enclave twice from the same top-level callback`() {
        val host = MockHost.loadMock<NestedCallbackEnclave>()
        host.start(null, null)
        val response = host.callEnclave(byteArrayOf(1)) { first ->
            val second = host.callEnclave(first + 3)!!
            host.callEnclave(second + 5)!! + 7
        }
        assertThat(response).isEqualTo(byteArrayOf(1, 2, 3, 4, 5, 4, 7, 6))
    }

    private fun EnclaveHost.recordCallbacksFromEnclave(bytes: ByteArray): Pair<ByteArray?, List<ByteArray>> {
        val callback = RecordingEnclaveCall()
        val response = callEnclave(bytes, callback)
        return Pair(response, callback.calls)
    }
}
