package com.r3.conclave.host

import com.r3.conclave.common.EnclaveMode
import com.r3.conclave.common.MockConfiguration
import com.r3.conclave.common.SHA256Hash
import com.r3.conclave.common.internal.StateManager
import com.r3.conclave.enclave.Enclave
import com.r3.conclave.host.internal.createMockHost
import com.r3.conclave.internaltesting.threadWithFuture
import com.r3.conclave.mail.PostOffice
import com.r3.conclave.utilities.internal.deserialise
import com.r3.conclave.utilities.internal.digest
import com.r3.conclave.utilities.internal.writeData
import org.assertj.core.api.Assertions.*
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import kotlin.random.Random

class EnclaveHostMockTest {
    private lateinit var host: EnclaveHost
    private var checkLeakedCallbacks = true

    @AfterEach
    fun `make sure the host is not leaking any callbacks`() {
        if (checkLeakedCallbacks && ::host.isInitialized) {
            val enclaveCallHandler = host.field("enclaveMessageHandler", EnclaveHost::class.java)

            @Suppress("UNCHECKED_CAST")
            val enclaveCalls = (enclaveCallHandler.field("threadIDToTransaction") as Map<Long, Any>).values.map {
                it.javaClass.getDeclaredField("stateManager").apply { isAccessible = true }.get(it) as StateManager<*>
            }
            assertThat(enclaveCalls.map { it.state.javaClass.simpleName }).containsOnly("Ready")
        }
    }

    @AfterEach
    fun `make sure the enclave is not leaking any callbacks`() {
        if (checkLeakedCallbacks && ::host.isInitialized) {
            val enclaveCallHandler = host.mockEnclave.field("enclaveMessageHandler", Enclave::class.java)

            @Suppress("UNCHECKED_CAST")
            val enclaveCalls = enclaveCallHandler.field("enclaveCalls") as Map<Long, StateManager<*>>
            // Check the only callbacks remaining are the ones to the enclave's receiveFromUntrustedHost, which is the
            // initial state.
            assertThat(enclaveCalls.values.map { it.state.field("receiveFromUntrustedHost") }).containsOnly(true)
        }
    }

    @Test
    fun `enclaveInstanceInfo before start`() {
        checkLeakedCallbacks = false
        val host = createMockHost(SimpleReturnEnclave::class.java)
        assertThatIllegalStateException().isThrownBy {
            host.enclaveInstanceInfo
        }.withMessage("The enclave host has not been started.")
    }

    @Test
    fun `callEnclave before start`() {
        checkLeakedCallbacks = false
        val host = createMockHost(SimpleReturnEnclave::class.java)
        assertThatIllegalStateException().isThrownBy {
            host.callEnclave(byteArrayOf())
        }.withMessage("The enclave host has not been started.")
        assertThatIllegalStateException().isThrownBy {
            host.callEnclave(byteArrayOf()) { it }
        }.withMessage("The enclave host has not been started.")
    }

    @Test
    fun `callEnclave after close`() {
        val host = createMockHost(SimpleReturnEnclave::class.java)
        host.start(null, null) { }
        host.close()
        assertThatExceptionOfType(IllegalStateException::class.java).isThrownBy {
            host.callEnclave(byteArrayOf())
        }.withMessage("The enclave host has been closed.")
        assertThatIllegalStateException().isThrownBy {
            host.callEnclave(byteArrayOf()) { it }
        }.withMessage("The enclave host has been closed.")
    }

    @Test
    fun `deliverMail before start`() {
        checkLeakedCallbacks = false
        val host = createMockHost(SimpleReturnEnclave::class.java)
        assertThatIllegalStateException().isThrownBy {
            host.deliverMail(byteArrayOf(), null)
        }.withMessage("The enclave host has not been started.")
        assertThatIllegalStateException().isThrownBy {
            host.deliverMail(byteArrayOf(), null) { it }
        }.withMessage("The enclave host has not been started.")
    }

    @Test
    fun `deliverMail after close`() {
        val host = createMockHost(SimpleReturnEnclave::class.java)
        host.start(null, null) { }
        host.close()
        assertThatExceptionOfType(IllegalStateException::class.java).isThrownBy {
            host.deliverMail(byteArrayOf(), null)
        }.withMessage("The enclave host has been closed.")
        assertThatIllegalStateException().isThrownBy {
            host.deliverMail(byteArrayOf(), null) { it }
        }.withMessage("The enclave host has been closed.")
    }

    @Test
    fun `calling into enclave which doesn't override receiveFromUntrustedHost`() {
        checkLeakedCallbacks = false
        val host = createMockHost(EnclaveWithoutHostBytesSupport::class.java)
        host.start(null, null) {  }
        assertThatExceptionOfType(UnsupportedOperationException::class.java).isThrownBy {
            host.callEnclave(byteArrayOf())
        }.withMessage("This enclave does not support local host communication.")
        assertThatExceptionOfType(UnsupportedOperationException::class.java).isThrownBy {
            host.callEnclave(byteArrayOf()) { it }
        }.withMessage("This enclave does not support local host communication.")
    }

    class EnclaveWithoutHostBytesSupport : Enclave()

    @Test
    fun `enclave does not respond to host message`() {
        host = createMockHost(NoOpEnclave::class.java)
        host.start(null, null) {  }
        val (response, callbacks) = host.recordCallbacksFromEnclave("abcd".toByteArray())
        assertThat(response).isNull()
        assertThat(callbacks).isEmpty()
    }

    class NoOpEnclave : Enclave() {
        override fun receiveFromUntrustedHost(bytes: ByteArray): ByteArray? = null
    }

    @Test
    fun `enclave response via receiveFromUntrustedHost return (ie no callback)`() {
        host = createMockHost(SimpleReturnEnclave::class.java)
        host.start(null, null) {  }
        val response = host.callEnclave(byteArrayOf(1))
        assertThat(response).isEqualTo(byteArrayOf(1, 2))
    }

    class SimpleReturnEnclave : Enclave() {
        override fun receiveFromUntrustedHost(bytes: ByteArray): ByteArray = bytes + 2
    }

    @Test
    fun `enclave response via callUntrustedHost`() {
        host = createMockHost(SimpleCallbackEnclave::class.java)
        host.start(null, null) {  }
        val (response, callbacks) = host.recordCallbacksFromEnclave(byteArrayOf(1))
        assertThat(response).isNull()
        assertThat(callbacks).containsOnly(byteArrayOf(1, 2))
    }

    @Test
    fun `enclave response via callUntrustedHost but host has no callback`() {
        host = createMockHost(SimpleCallbackEnclave::class.java)
        host.start(null, null) {  }
        assertThatIllegalArgumentException().isThrownBy {
            host.callEnclave("abcd".toByteArray())
        }.withMessage("Enclave responded via callUntrustedHost but a callback was not provided to callEnclave.")
    }

    class SimpleCallbackEnclave : Enclave() {
        override fun receiveFromUntrustedHost(bytes: ByteArray): ByteArray? {
            callUntrustedHost(bytes + 2)
            return null
        }
    }

    @Test
    fun `enclave response via multiple callUntrustedHost and receiveFromUntrustedHost return`() {
        host = createMockHost(CallbacksAndReturnEnclave::class.java)
        host.start(null, null) {  }
        val (response, callbacks) = host.recordCallbacksFromEnclave(byteArrayOf(1))
        assertThat(response).isEqualTo(byteArrayOf(1, 4))
        assertThat(callbacks).containsExactly(byteArrayOf(1, 2), byteArrayOf(1, 3))
    }

    class CallbacksAndReturnEnclave : Enclave() {
        override fun receiveFromUntrustedHost(bytes: ByteArray): ByteArray {
            callUntrustedHost(bytes + 2)
            callUntrustedHost(bytes + 3)
            return bytes + 4
        }
    }

    @Test
    fun `back and forth between host and enclave (via the host returning from its callback)`() {
        host = createMockHost(SimpleBackAndForthEnclave::class.java)
        host.start(null, null) {  }
        val response = host.callEnclave(byteArrayOf(1)) { fromEnclave -> fromEnclave + 3 }
        assertThat(response).isEqualTo(byteArrayOf(1, 2, 3, 4, 3, 5))
    }

    class SimpleBackAndForthEnclave : Enclave() {
        override fun receiveFromUntrustedHost(bytes: ByteArray): ByteArray {
            val response1 = callUntrustedHost(bytes + 2)!!
            assertThat(response1).isEqualTo(byteArrayOf(1, 2, 3))
            val response2 = callUntrustedHost(response1 + 4)!!
            assertThat(response2).isEqualTo(byteArrayOf(1, 2, 3, 4, 3))
            return response2 + 5
        }
    }

    @Test
    fun `host calls back into the enclave from its own callback`() {
        host = createMockHost(NestedCallbackEnclave::class.java)
        host.start(null, null) {  }
        val response = host.callEnclave(byteArrayOf(1)) { fromEnclave ->
            host.callEnclave(fromEnclave + 3)!! + 5
        }
        assertThat(response).isEqualTo(byteArrayOf(1, 2, 3, 4, 5, 6))
    }

    class NestedCallbackEnclave : Enclave() {
        override fun receiveFromUntrustedHost(bytes: ByteArray): ByteArray {
            return callUntrustedHost(bytes + 2) { fromHost -> fromHost + 4 }!! + 6
        }
    }

    @Test
    fun `host calls back into the enclave but enclave has no callback`() {
        host = createMockHost(SimpleCallbackEnclave::class.java)
        host.start(null, null) {  }
        assertThatIllegalStateException().isThrownBy {
            host.callEnclave(byteArrayOf(1)) { fromEnclave -> host.callEnclave(fromEnclave + 3) }
        }
            .withMessage("The enclave has not provided a callback to callUntrustedHost to receive the host's call back in.")
    }

    @Test
    fun `host calls back into the enclave with a 2nd layer callback, which gets invoked twice`() {
        host = createMockHost(ComplexCallbackEnclave::class.java)
        host.start(null, null) {  }
        val response = host.callEnclave(byteArrayOf(1)) { first ->
            host.callEnclave(first + 3) { second -> second + 5 }!! + 8
        }
        assertThat(response).isEqualTo(byteArrayOf(1, 2, 3, 4, 5, 6, 5, 7, 8, 9))
    }

    class ComplexCallbackEnclave : Enclave() {
        override fun receiveFromUntrustedHost(bytes: ByteArray): ByteArray {
            return callUntrustedHost(bytes + 2) { fromHost ->
                val response1 = callUntrustedHost(fromHost + 4)!! + 6
                val response2 = callUntrustedHost(response1)!!
                response2 + 7
            }!! + 9
        }
    }

    @Test
    fun `host calls back into the enclave twice from the same top-level callback`() {
        host = createMockHost(NestedCallbackEnclave::class.java)
        host.start(null, null) {  }
        val response = host.callEnclave(byteArrayOf(1)) { first ->
            val second = host.callEnclave(first + 3)!!
            host.callEnclave(second + 5)!! + 7
        }
        assertThat(response).isEqualTo(byteArrayOf(1, 2, 3, 4, 5, 4, 7, 6))
    }

    @Test
    fun `exception thrown by enclave in receiveFromUntrustedHost does not impact subsequent callEnclave`() {
        host = createMockHost(ThrowingEnclave::class.java)
        host.start(null, null) {  }

        assertThatExceptionOfType(RuntimeException::class.java).isThrownBy {
            host.callEnclave(throwCommand(throwException = true, message = "Help!"))
        }.withMessage("Help!")

        val response = host.callEnclave(throwCommand(throwException = false, message = "OK!"))
        assertThat(response).isEqualTo("OK!".toByteArray())
    }

    class ThrowingEnclave : Enclave() {
        override fun receiveFromUntrustedHost(bytes: ByteArray): ByteArray {
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
        host = createMockHost(SimpleCallbackEnclave::class.java)
        host.start(null, null) {  }

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
        val enclave = createMockHost(CatchExceptionOnFirstCallEnclave::class.java).let {
            it.start(null, null) {  }
            host = it
            it.mockEnclave as CatchExceptionOnFirstCallEnclave
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

    class CatchExceptionOnFirstCallEnclave : Enclave() {
        var caughtException: RuntimeException? = null

        override fun receiveFromUntrustedHost(bytes: ByteArray): ByteArray? {
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
        host = createMockHost(CallbackThrowingEnclave::class.java)
        host.start(null, null) {  }

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
        assertThat(secondLevelCallSuccessful).isFalse

        val response = host.callEnclave(byteArrayOf(1)) { fromEnclave ->
            fromEnclave + host.callEnclave(throwCommand(throwException = false, message = "Yay!"))!!
        }
        assertThat(response).isEqualTo(byteArrayOf(1, 2) + "Yay!".toByteArray())
    }

    @Test
    fun `exception thrown by enclave in its callback does not impact subsequent 2nd-level callEnclave`() {
        host = createMockHost(CallbackThrowingEnclave::class.java)
        host.start(null, null) {  }

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

    class CallbackThrowingEnclave : Enclave() {
        override fun receiveFromUntrustedHost(bytes: ByteArray): ByteArray? {
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

    @Test
    fun `load enclave with private c'tor`() {
        checkLeakedCallbacks = false
        host = createMockHost(PrivateCtorEnclave::class.java)
        host.start(null, null) {  }
    }

    class PrivateCtorEnclave private constructor() : Enclave()

    @ParameterizedTest
    @ValueSource(strings = ["PostOffice.create()", "EnclaveInstanceInfo.createPostOffice()"])
    fun `cannot create PostOffice directly when inside enclave`(source: String) {
        class CreatePostOfficeEnclave : Enclave() {
            override fun receiveFromUntrustedHost(bytes: ByteArray): ByteArray? {
                when (String(bytes)) {
                    "PostOffice.create()" -> PostOffice.create(enclaveInstanceInfo.encryptionKey)
                    "EnclaveInstanceInfo.createPostOffice()" -> enclaveInstanceInfo.createPostOffice()
                }
                return null
            }
        }
        host = createMockHost(CreatePostOfficeEnclave::class.java)
        host.start(null, null) {  }

        // Outside of the enclave is fine, before and after
        host.enclaveInstanceInfo.createPostOffice()

        // But not while inside
        assertThatIllegalStateException()
            .isThrownBy { host.callEnclave(source.toByteArray()) }
            .withMessage("Use one of the Enclave.postOffice() methods for getting a PostOffice instance when inside an enclave.")

        host.enclaveInstanceInfo.createPostOffice()
    }

    @Test
    fun `simple sample test, testing ability to access enclave internals`() {
        val host = createMockHost(PreviousValueEnclave::class.java)
        host.start(null, null) {  }

        val enclave = host.mockEnclave as PreviousValueEnclave

        assertThat(enclave.previousValue).isNull()
        val firstResponse = host.callEnclave("Hello".toByteArray())
        assertThat(firstResponse).isNull()

        assertThat(enclave.previousValue).isEqualTo("Hello".toByteArray())
        val secondResponse = host.callEnclave("World".toByteArray())
        assertThat(secondResponse).isEqualTo("Hello".toByteArray())
        assertThat(enclave.previousValue).isEqualTo("World".toByteArray())
    }

    @Test
    fun `enclaveInfo values`() {
        val host = createMockHost(PreviousValueEnclave::class.java)
        host.start(null, null) {  }
        assertThat(host.enclaveInstanceInfo.enclaveInfo.enclaveMode).isEqualTo(EnclaveMode.MOCK)
        assertThat(host.enclaveInstanceInfo.enclaveInfo.codeHash.bytes.contentEquals(digest("SHA-256") { update(PreviousValueEnclave::class.java.name.toByteArray()) }))
        assertThat(host.enclaveInstanceInfo.enclaveInfo.codeSigningKeyHash.bytes).containsOnly(0)
    }

    @Test
    fun `enclaveInfo values custom MRENCLAVE`() {
        val mockConfiguration = MockConfiguration()
        val hash = SHA256Hash.wrap(Random.nextBytes(32))
        mockConfiguration.codeHash = hash
        val host = createMockHost(PreviousValueEnclave::class.java, mockConfiguration)
        host.start(null, null) {  }
        assertThat(host.enclaveInstanceInfo.enclaveInfo.enclaveMode).isEqualTo(EnclaveMode.MOCK)
        assertThat(host.enclaveInstanceInfo.enclaveInfo.codeHash).isEqualTo(hash)
    }

    @Test
    fun `enclaveInfo values custom MRSIGNER`() {
        val mockConfiguration = MockConfiguration()
        val hash = SHA256Hash.wrap(Random.nextBytes(32))
        mockConfiguration.codeSigningKeyHash = hash
        val host = createMockHost(PreviousValueEnclave::class.java, mockConfiguration)
        host.start(null, null) {  }
        assertThat(host.enclaveInstanceInfo.enclaveInfo.enclaveMode).isEqualTo(EnclaveMode.MOCK)
        assertThat(host.enclaveInstanceInfo.enclaveInfo.codeSigningKeyHash).isEqualTo(hash)
    }

    class EnclaveWithHooks : Enclave() {

        var onStartupWasCalled: Boolean = false
        var onShutdownWasCalled: Boolean = false

        override fun onStartup() {
            onStartupWasCalled = true
        }

        override fun onShutdown() {
            onShutdownWasCalled = true
        }
    }

    @Test
    fun `enclave onStartup and onShutdown are called at the right moment`() {
        val host = createMockHost(EnclaveWithHooks::class.java)
        val enclave = host.mockEnclave as EnclaveWithHooks

        // The host was only created but the initialization process has not started yet
        assertFalse(enclave.onStartupWasCalled, "The host was created but not initialised. onStartup should not have been called")
        assertFalse(enclave.onShutdownWasCalled, "The host was created but not uninitialised. onShutdown should not have been called")

        host.start(null, null) { }

        // The host is now running so it must have been initialised
        assertTrue(enclave.onStartupWasCalled, "The host was initialised. onStartup should have been called")
        assertFalse(enclave.onShutdownWasCalled, "The host was initialised but not uninitialised. onShutdown should not have been called")

        host.close()

        // The host has now been stopped so it must have been uninitialised
        assertTrue(enclave.onStartupWasCalled, "The host was initialised and uninitialised. onStartup should have been called")
        assertTrue(enclave.onShutdownWasCalled, "The host was initialised and uninitialised. onShutdown should have been called")
    }

    @Test
    fun `enclave onShutdown is not called when the enclave is not initialised even if the host is closed`() {
        val host = createMockHost(EnclaveWithHooks::class.java)
        val enclave = host.mockEnclave as EnclaveWithHooks

        // The host was only created but the initialization process has not started yet
        assertFalse(enclave.onShutdownWasCalled, "The host was created but not uninitialised. onShutdown should not have been called")

        host.close()

        // The host has now been stopped so it must have been uninitialised
        assertFalse(enclave.onShutdownWasCalled, "The host was closed even though it was not initialised. onShutdown should not been called")
    }

    class PreviousValueEnclave : Enclave() {
        var previousValue: ByteArray? = null

        override fun receiveFromUntrustedHost(bytes: ByteArray): ByteArray? {
            val previousValue = this.previousValue
            this.previousValue = bytes
            return previousValue
        }
    }

    @Test
    fun `calling callUntrustedHost from a separate thread throws exception`() {
        host = createMockHost(CallUntrustedHostInSeparateThreadEnclave::class.java)
        host.start(null, null) { }
        assertThatExceptionOfType(RuntimeException::class.java).isThrownBy {
            host.callEnclave(byteArrayOf()) { it }
        }.withMessageEndingWith("may not attempt to call out to the host outside the context of a call.")
    }

    class CallUntrustedHostInSeparateThreadEnclave : Enclave() {
        override fun receiveFromUntrustedHost(bytes: ByteArray): ByteArray {
            val future = threadWithFuture {
                callUntrustedHost(bytes)!!
            }
            return future.get()!!
        }
    }

    private fun EnclaveHost.recordCallbacksFromEnclave(bytes: ByteArray): Pair<ByteArray?, List<ByteArray>> {
        val callback = RecordingCallback()
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
