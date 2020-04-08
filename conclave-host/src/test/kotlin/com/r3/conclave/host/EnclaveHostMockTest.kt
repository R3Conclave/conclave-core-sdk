package com.r3.conclave.host

import com.r3.conclave.common.EnclaveMode
import com.r3.conclave.common.enclave.EnclaveCall
import com.r3.conclave.enclave.Enclave
import com.r3.conclave.enclave.callUntrustedHost
import com.r3.sgx.core.common.ThrowingErrorHandler
import com.r3.sgx.core.enclave.RootEnclave
import com.r3.sgx.testing.MockEcallSender
import org.assertj.core.api.Assertions.*
import org.junit.jupiter.api.Test
import java.security.PublicKey

class EnclaveHostMockTest {
    @Test
    fun `calling into enclave which doesn't implement EnclaveCall`() {
        val host = hostTo(EnclaveWithoutEnclaveCall())
        host.start(null, null)
        assertThatIllegalArgumentException().isThrownBy {
            host.callEnclave(byteArrayOf())
        }.withMessage("Enclave does not implement EnclaveCall to receive messages from the host.")
        assertThatIllegalArgumentException().isThrownBy {
            host.callEnclave(byteArrayOf()) { it }
        }.withMessage("Enclave does not implement EnclaveCall to receive messages from the host.")
    }

    private class EnclaveWithoutEnclaveCall : Enclave()

    @Test
    fun `enclave doesn't response to host message`() {
        val host = hostTo(NoOpEnclave())
        host.start(null, null)
        val (response, callbacks) = host.recordCallbacksFromEnclave("abcd".toByteArray())
        assertThat(response).isNull()
        assertThat(callbacks).isEmpty()
    }

    private class NoOpEnclave : EnclaveCall, Enclave() {
        override fun invoke(bytes: ByteArray): ByteArray? = null
    }

    @Test
    fun `enclave response via invoke return (ie no callback)`() {
        val host = hostTo(SimpleReturnEnclave())
        host.start(null, null)
        val response = host.callEnclave(byteArrayOf(1))
        assertThat(response).isEqualTo(byteArrayOf(1, 2))
    }

    private class SimpleReturnEnclave : EnclaveCall, Enclave() {
        override fun invoke(bytes: ByteArray): ByteArray? = bytes + 2
    }

    @Test
    fun `enclave response via callUntrustedHost`() {
        val host = hostTo(SimpleCallbackEnclave())
        host.start(null, null)
        val (response, callbacks) = host.recordCallbacksFromEnclave(byteArrayOf(1))
        assertThat(response).isNull()
        assertThat(callbacks).containsOnly(byteArrayOf(1, 2))
    }

    @Test
    fun `enclave response via callUntrustedHost but host has no callback`() {
        val host = hostTo(SimpleCallbackEnclave())
        host.start(null, null)
        assertThatIllegalArgumentException().isThrownBy {
            host.callEnclave("abcd".toByteArray())
        }.withMessage("Enclave responded via callUntrustedHost but a callback was not provided to callEnclave.")
    }

    private class SimpleCallbackEnclave : EnclaveCall, Enclave() {
        override fun invoke(bytes: ByteArray): ByteArray? {
            callUntrustedHost(bytes + 2)
            return null
        }
    }

    @Test
    fun `enclave response via multiple callUntrustedHost and invoke return`() {
        val host = hostTo(CallbacksAndReturnEnclave())
        host.start(null, null)
        val (response, callbacks) = host.recordCallbacksFromEnclave(byteArrayOf(1))
        assertThat(response).isEqualTo(byteArrayOf(1, 4))
        assertThat(callbacks).containsExactly(byteArrayOf(1, 2), byteArrayOf(1, 3))
    }

    private class CallbacksAndReturnEnclave : EnclaveCall, Enclave() {
        override fun invoke(bytes: ByteArray): ByteArray? {
            callUntrustedHost(bytes + 2)
            callUntrustedHost(bytes + 3)
            return bytes + 4
        }
    }

    @Test
    fun `back and forth between host and enclave (via the host returning from its callback)`() {
        val host = hostTo(SimpleBackAndForthEnclave())
        host.start(null, null)
        val response = host.callEnclave(byteArrayOf(1)) { fromEnclave -> fromEnclave + 3 }
        assertThat(response).isEqualTo(byteArrayOf(1, 2, 3, 4, 3, 5))
    }

    private class SimpleBackAndForthEnclave : EnclaveCall, Enclave() {
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
        val host = hostTo(NestedCallbackEnclave())
        host.start(null, null)
        val response = host.callEnclave(byteArrayOf(1)) { fromEnclave ->
            host.callEnclave(fromEnclave + 3)!! + 5
        }
        assertThat(response).isEqualTo(byteArrayOf(1, 2, 3, 4, 5, 6))
    }

    private class NestedCallbackEnclave : EnclaveCall, Enclave() {
        override fun invoke(bytes: ByteArray): ByteArray? {
            return callUntrustedHost(bytes + 2) { fromHost -> fromHost + 4 }!! + 6
        }
    }

    @Test
    fun `host calls back into the enclave but enclave has no callback`() {
        val host = hostTo(SimpleCallbackEnclave())
        host.start(null, null)
        assertThatIllegalArgumentException().isThrownBy {
            host.callEnclave(byteArrayOf(1)) { fromEnclave -> host.callEnclave(fromEnclave + 3) }
        }.withMessage("Enclave has not provided a callback to callUntrustedHost to receive the host's call back in.")
    }

    @Test
    fun `host calls back into the enclave with a 2nd layer callback, which gets invoked twice`() {
        val host = hostTo(ComplexCallbackEnclave())
        host.start(null, null)
        val response = host.callEnclave(byteArrayOf(1)) { first ->
            host.callEnclave(first + 3) { second -> second + 5 }!! + 8
        }
        assertThat(response).isEqualTo(byteArrayOf(1, 2, 3, 4, 5, 6, 5, 7, 8, 9))
    }

    private class ComplexCallbackEnclave : EnclaveCall, Enclave() {
        override fun invoke(bytes: ByteArray): ByteArray? {
            return callUntrustedHost(bytes + 2) { fromHost ->
                val response1 = callUntrustedHost(fromHost + 4)!! + 6
                val response2 = callUntrustedHost(response1)!!
                response2 + 7
            }!! + 9
        }
    }

    @Test
    fun `enclaveInstanceInfo before start`() {
        val host = hostTo(SimpleReturnEnclave())
        assertThatIllegalStateException().isThrownBy {
            host.enclaveInstanceInfo
        }.withMessage("Enclave has not been started.")
    }

    @Test
    fun `host verifies signature created by enclave`() {
        val enclave = SigningEnclave()
        val host = hostTo(enclave)
        host.start(null, null)
        assertThat(host.enclaveInstanceInfo.dataSigningKey).isEqualTo(enclave.exposedSignatureKey)
        val message = "Hello World".toByteArray()
        val signature = host.callEnclave(message)!!
        host.enclaveInstanceInfo.verifier().apply {
            update(message)
            assertThat(verify(signature)).isTrue()
        }
    }

    private class SigningEnclave : EnclaveCall, Enclave() {
        val exposedSignatureKey: PublicKey get() = signatureKey

        override fun invoke(bytes: ByteArray): ByteArray {
            return signer().run {
                update(bytes)
                sign()
            }
        }
    }

    private fun hostTo(enclave: Enclave): EnclaveHost {
        // The use of reflection is not ideal but it means we don't expose something that shouldn't be in the public API.
        val rootEnclave = Enclave::class.java.getDeclaredField("rootEnclave").apply { isAccessible = true }.get(enclave) as RootEnclave
        val handle = MockEcallSender(ThrowingErrorHandler(), rootEnclave)
        return EnclaveHost.create(EnclaveMode.SIMULATION, handle, fileToDelete = null, isMock = true)
    }

    private fun EnclaveHost.recordCallbacksFromEnclave(bytes: ByteArray): Pair<ByteArray?, List<ByteArray>> {
        val callbacks = ArrayList<ByteArray>()
        val response = callEnclave(bytes) {
            callbacks += it
            null
        }
        return Pair(response, callbacks)
    }
}
