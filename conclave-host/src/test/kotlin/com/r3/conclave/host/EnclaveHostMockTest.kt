package com.r3.conclave.host

import com.r3.conclave.common.EnclaveCall
import com.r3.conclave.common.internal.StateManager
import com.r3.conclave.enclave.Enclave
import com.r3.conclave.internaltesting.RecordingEnclaveCall
import com.r3.conclave.testing.MockHost
import com.r3.conclave.utilities.internal.deserialise
import com.r3.conclave.utilities.internal.writeData
import org.assertj.core.api.Assertions.*
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test

class EnclaveHostMockTest {
    private lateinit var host: MockHost<*>
    private var checkLeakedCallbacks = true

    @AfterEach
    fun `make sure the host is not leaking any callbacks`() {
        if (checkLeakedCallbacks) {
            val enclaveCallHandler = host.field("enclaveMessageHandler", EnclaveHost::class.java)
            @Suppress("UNCHECKED_CAST")
            val enclaveCalls = (enclaveCallHandler.field("threadIDToTransaction") as Map<Long, Any>).values.map {
                it.javaClass.getDeclaredField("stateManager").also { it.isAccessible = true }.get(it) as StateManager<*>
            }
            assertThat(enclaveCalls.map { it.state.javaClass.simpleName }).containsOnly("Ready")
        }
    }

    @AfterEach
    fun `make sure the enclave is not leaking any callbacks`() {
        if (checkLeakedCallbacks) {
            val enclaveCallHandler = host.enclave.field("enclaveMessageHandler", Enclave::class.java)
            @Suppress("UNCHECKED_CAST")
            val enclaveCalls = enclaveCallHandler.field("enclaveCalls") as Map<Long, StateManager<*>>
            assertThat(enclaveCalls.values.map { it.state.field("callback") }).containsOnly(host.enclave)
        }
    }

    @Test
    fun `enclaveInstanceInfo before start`() {
        checkLeakedCallbacks = false
        val host = MockHost.loadMock<SimpleReturnEnclave>()
        assertThatIllegalStateException().isThrownBy {
            host.enclaveInstanceInfo
        }.withMessage("Enclave has not been started.")
    }

    @Test
    fun `calling into enclave which doesn't implement EnclaveCall`() {
        checkLeakedCallbacks = false
        val host = MockHost.loadMock<EnclaveWithoutEnclaveCall>()
        host.start(null, null, null)
        assertThatIllegalStateException().isThrownBy {
            host.callEnclave(byteArrayOf())
        }.withMessage("The enclave does not implement EnclaveCall to receive messages from the host.")
        assertThatIllegalStateException().isThrownBy {
            host.callEnclave(byteArrayOf()) { it }
        }.withMessage("The enclave does not implement EnclaveCall to receive messages from the host.")
    }

    class EnclaveWithoutEnclaveCall : Enclave()

    @Test
    fun `enclave does not respond to host message`() {
        host = MockHost.loadMock<NoOpEnclave>()
        host.start(null, null, null)
        val (response, callbacks) = host.recordCallbacksFromEnclave("abcd".toByteArray())
        assertThat(response).isNull()
        assertThat(callbacks).isEmpty()
    }

    class NoOpEnclave : EnclaveCall, Enclave() {
        override fun invoke(bytes: ByteArray): ByteArray? = null
    }

    @Test
    fun `enclave response via invoke return (ie no callback)`() {
        host = MockHost.loadMock<SimpleReturnEnclave>()
        host.start(null, null, null)
        val response = host.callEnclave(byteArrayOf(1))
        assertThat(response).isEqualTo(byteArrayOf(1, 2))
    }

    class SimpleReturnEnclave : EnclaveCall, Enclave() {
        override fun invoke(bytes: ByteArray): ByteArray? = bytes + 2
    }

    @Test
    fun `enclave response via callUntrustedHost`() {
        host = MockHost.loadMock<SimpleCallbackEnclave>()
        host.start(null, null, null)
        val (response, callbacks) = host.recordCallbacksFromEnclave(byteArrayOf(1))
        assertThat(response).isNull()
        assertThat(callbacks).containsOnly(byteArrayOf(1, 2))
    }

    @Test
    fun `enclave response via callUntrustedHost but host has no callback`() {
        host = MockHost.loadMock<SimpleCallbackEnclave>()
        host.start(null, null, null)
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
        host = MockHost.loadMock<CallbacksAndReturnEnclave>()
        host.start(null, null, null)
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
        host = MockHost.loadMock<SimpleBackAndForthEnclave>()
        host.start(null, null, null)
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
        host = MockHost.loadMock<NestedCallbackEnclave>()
        host.start(null, null, null)
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
        host = MockHost.loadMock<SimpleCallbackEnclave>()
        host.start(null, null, null)
        assertThatIllegalArgumentException().isThrownBy {
            host.callEnclave(byteArrayOf(1)) { fromEnclave -> host.callEnclave(fromEnclave + 3) }
        }.withMessage("The enclave has not provided a callback to callUntrustedHost to receive the host's call back in.")
    }

    @Test
    fun `host calls back into the enclave with a 2nd layer callback, which gets invoked twice`() {
        host = MockHost.loadMock<ComplexCallbackEnclave>()
        host.start(null, null, null)
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
        host = MockHost.loadMock<NestedCallbackEnclave>()
        host.start(null, null, null)
        val response = host.callEnclave(byteArrayOf(1)) { first ->
            val second = host.callEnclave(first + 3)!!
            host.callEnclave(second + 5)!! + 7
        }
        assertThat(response).isEqualTo(byteArrayOf(1, 2, 3, 4, 5, 4, 7, 6))
    }

    @Test
    fun `exception thrown by enclave in invoke does not impact subsequent callEnclave`() {
        host = MockHost.loadMock<InvokeThrowingEnclave>()
        host.start(null, null, null)

        assertThatExceptionOfType(RuntimeException::class.java).isThrownBy {
            host.callEnclave(throwCommand(throwException = true, message = "Help!"))
        }.withMessage("Help!")

        val response = host.callEnclave(throwCommand(throwException = false, message = "OK!"))
        assertThat(response).isEqualTo("OK!".toByteArray())
    }

    class InvokeThrowingEnclave : Enclave(), EnclaveCall {
        override fun invoke(bytes: ByteArray): ByteArray? {
            val (throwException, message) = bytes.throwCommand()
            if (throwException) {
                throw RuntimeException(message)
            } else {
                return message.toByteArray()
            }
        }
    }

    @Test
    fun `exception thrown by host in its callback does not impact subsequent callEnclave`() {
        host = MockHost.loadMock<SimpleCallbackEnclave>()
        host.start(null, null, null)

        assertThatExceptionOfType(RuntimeException::class.java).isThrownBy {
            host.callEnclave(byteArrayOf(1)) {
                throw RuntimeException("Help!")
            }
        }.withMessage("Help!")

        val (response, callbacks) = host.recordCallbacksFromEnclave(byteArrayOf(1))
        assertThat(response).isNull()
        assertThat(callbacks).containsOnly(byteArrayOf(1, 2))
    }

    @Test
    fun `exception thrown by host in its callback does not impact subsequent callUntrustedHost`() {
        val enclave = MockHost.loadMock<CatchExceptionOnFirstCallEnclave>().let {
            it.start(null, null, null)
            host = it
            it.enclave
        }

        var firstCall = true
        val response = host.callEnclave(byteArrayOf(1)) { fromEnclave ->
            if (firstCall) {
                firstCall = false
                throw RuntimeException("Help!")
            } else {
                fromEnclave + 4
            }
        }

        assertThat(enclave.caughtException).hasMessage("Help!")
        assertThat(response).isEqualTo(byteArrayOf(1, 3, 4))
    }

    class CatchExceptionOnFirstCallEnclave : EnclaveCall, Enclave() {
        var caughtException: RuntimeException? = null

        override fun invoke(bytes: ByteArray): ByteArray? {
            try {
                callUntrustedHost(bytes + 2)
            } catch (e: RuntimeException) {
                caughtException = e
            }
            return callUntrustedHost(bytes + 3)
        }
    }

    @Test
    fun `exception thrown by enclave in its callback does not impact subsequent top-level callEnclave`() {
        host = MockHost.loadMock<CallbackThrowingEnclave>()
        host.start(null, null, null)

        var enclaveCallbackResponse: ByteArray? = null
        var secondLevelCallSuccessful = false
        assertThatExceptionOfType(RuntimeException::class.java).isThrownBy {
            host.callEnclave(byteArrayOf(1)) { fromEnclave ->
                enclaveCallbackResponse = fromEnclave
                host.callEnclave(throwCommand(throwException = true, message = "Boom!"))
                secondLevelCallSuccessful = true
                null
            }
        }.withMessage("Boom!")
        assertThat(enclaveCallbackResponse).isEqualTo(byteArrayOf(1, 2))
        assertThat(secondLevelCallSuccessful).isFalse()

        val response = host.callEnclave(byteArrayOf(1)) { fromEnclave ->
            fromEnclave + host.callEnclave(throwCommand(throwException = false, message = "Yay!"))!!
        }
        assertThat(response).isEqualTo(byteArrayOf(1, 2) + "Yay!".toByteArray())
    }

    @Test
    fun `exception thrown by enclave in its callback does not impact subsequent 2nd-level callEnclave`() {
        host = MockHost.loadMock<CallbackThrowingEnclave>()
        host.start(null, null, null)

        var firstCallException: RuntimeException? = null
        val response = host.callEnclave(byteArrayOf(1)) { fromEnclave ->
            try {
                host.callEnclave(throwCommand(throwException = true, message = "Boom!"))
            } catch (e: RuntimeException) {
                firstCallException = e
            }
            val secondCallEnclaveResult = host.callEnclave(throwCommand(throwException = false, message = "Yay!"))
            fromEnclave + secondCallEnclaveResult!!
        }
        assertThat(firstCallException).hasMessage("Boom!")
        assertThat(response).isEqualTo(byteArrayOf(1, 2) + "Yay!".toByteArray())
    }

    class CallbackThrowingEnclave : EnclaveCall, Enclave() {
        override fun invoke(bytes: ByteArray): ByteArray? {
            return callUntrustedHost(bytes + 2) { fromHost ->
                val (throwException, message) = fromHost.throwCommand()
                if (throwException) {
                    throw RuntimeException(message)
                } else {
                    message.toByteArray()
                }
            }
        }
    }

    private fun EnclaveHost.recordCallbacksFromEnclave(bytes: ByteArray): Pair<ByteArray?, List<ByteArray>> {
        val callback = RecordingEnclaveCall()
        val response = callEnclave(bytes, callback)
        return Pair(response, callback.calls)
    }

    private fun Any.field(name: String, clazz: Class<*> = javaClass): Any {
        return clazz.getDeclaredField(name).apply { isAccessible = true }.get(this)
    }
}

private fun throwCommand(throwException: Boolean, message: String): ByteArray {
    return writeData {
        write(if (throwException) 1 else 0)
        writeUTF(message)
    }
}

private fun ByteArray.throwCommand(): Pair<Boolean, String> {
    return deserialise {
        val throwException = read() != 0
        val message = readUTF()
        Pair(throwException, message)
    }
}
