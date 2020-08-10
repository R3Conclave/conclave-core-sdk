package com.r3.conclave.enclave.internal

import com.r3.conclave.common.OpaqueBytes
import com.r3.conclave.common.internal.PlaintextAndEnvelope
import com.r3.conclave.common.internal.handler.ExceptionSendingHandler
import com.r3.conclave.common.internal.handler.HandlerConnected
import com.r3.conclave.common.internal.handler.Sender
import com.r3.conclave.dynamictesting.TestEnclaves
import com.r3.conclave.enclave.Enclave
import com.r3.conclave.utilities.internal.getRemainingBytes
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.RegisterExtension

class SealingTest {
    companion object {
        @JvmField
        @RegisterExtension
        val testEnclaves = TestEnclaves()

        // Reusable handlers for seal / unseal tests.
        val sealUnsealRecordingHandler1 = SealUnsealRecordingHandler()
        val sealUnsealRecordingHandler2 = SealUnsealRecordingHandler()
        val throwHandler1 = ThrowFromHandler(sealUnsealRecordingHandler1)
        val throwHandler2 = ThrowFromHandler(sealUnsealRecordingHandler2)

        @AfterAll
        @JvmStatic
        internal fun afterAll() {
            EnclaveRecycler.clear()
        }
    }

    class SealUnsealEnclaveHandler(private val env: EnclaveEnvironment) : SealUnsealHandler() {
        override fun onReceiveUnsealedData(connection: SealUnsealSender, plaintextAndEnvelope: PlaintextAndEnvelope) {
            connection.sendSealedData(env.sealData(plaintextAndEnvelope))
        }

        override fun onReceiveSealedData(connection: SealUnsealSender, sealedData: ByteArray) {
            connection.sendUnsealedData(env.unsealData(sealedData))
        }
    }

    /**
     * An enclave for sealing and unsealing data.
     */
    class SealUnsealEnclave : InternalEnclave, Enclave() {
        override fun internalInitialise(env: EnclaveEnvironment, upstream: Sender): HandlerConnected<*> {
            return HandlerConnected.connect(SealUnsealEnclaveHandler(env), upstream)
        }
    }

    /**
     * Another enclave for sealing and unsealing data.
     * With distinct measurement than [SealUnsealEnclave].
     */
    class SealUnsealEnclaveAux : InternalEnclave, Enclave() {
        override fun internalInitialise(env: EnclaveEnvironment, upstream: Sender): HandlerConnected<*> {
            return HandlerConnected.connect(SealUnsealEnclaveHandler(env), upstream)
        }
    }

    /**
     * An enclave which is able to handle exceptions.
     */
    class SealUnsealEnclaveExceptionHandler : InternalEnclave, Enclave() {
        override fun internalInitialise(env: EnclaveEnvironment, upstream: Sender): HandlerConnected<*> {
            val connected = HandlerConnected.connect(ExceptionSendingHandler(exposeErrors = true), upstream)
            connected.connection.setDownstream(SealUnsealEnclaveHandler(env))
            return connected
        }
    }

    @Test
    fun `seal and unseal data`() {
        val connection = testEnclaves.createOrGetEnclave(
                handler = sealUnsealRecordingHandler1,
                enclaveClass = SealUnsealEnclave::class.java,
                keyGenInput = "signerA"
        )

        val toBeSealed = PlaintextAndEnvelope(OpaqueBytes("Sealing Hello World!".toByteArray()))
        connection.sendUnsealedData(toBeSealed) // Seal request.
        connection.sendSealedData(sealUnsealRecordingHandler1.pollLastReceivedSealedData.getRemainingBytes()) // Unseal request.
        val unsealed = sealUnsealRecordingHandler1.pollLastReceivedUnsealedData// Unsealed data.
        assertEquals(toBeSealed.plaintext, unsealed.plaintext)
    }

    @Test
    fun `seal in one enclave and unseal in another instance of the same enclave`() {
        val toBeSealed = PlaintextAndEnvelope(OpaqueBytes("Sealing Hello World!".toByteArray()))
        val connection1 = testEnclaves.createOrGetEnclave(
                handler = sealUnsealRecordingHandler1,
                enclaveClass = SealUnsealEnclave::class.java,
                keyGenInput = "signerA"
        )
        val connection2 = testEnclaves.createOrGetEnclave(
                handler = sealUnsealRecordingHandler2,
                enclaveClass = SealUnsealEnclave::class.java,
                keyGenInput = "signerA"
        )
        assertNotEquals(connection1, connection2)
        // Seal using the 1st enclave.
        connection1.sendUnsealedData(toBeSealed)
        val unsealReq = sealUnsealRecordingHandler1.pollLastReceivedSealedData
        // Unseal using the 2nd enclave.
        connection2.sendSealedData(sealedData = unsealReq.getRemainingBytes())
        val unsealed = sealUnsealRecordingHandler2.pollLastReceivedUnsealedData
        assertEquals(toBeSealed.plaintext, unsealed.plaintext)
    }

    @Test
    fun `seal in one enclave and unseal in another enclave`() {
        val toBeSealed = PlaintextAndEnvelope(OpaqueBytes("Sealing Hello World!".toByteArray()))
        val connection1 = testEnclaves.createOrGetEnclave(
                handler = sealUnsealRecordingHandler1,
                enclaveClass = SealUnsealEnclave::class.java,
                keyGenInput = "signerA"
        )
        val connection2 = testEnclaves.createOrGetEnclave(
                handler = sealUnsealRecordingHandler2,
                enclaveClass = SealUnsealEnclaveAux::class.java, // Distinct enclave measurement.
                keyGenInput = "signerA"
        )
        assertNotEquals(connection1, connection2)
        // Seal using the 1st enclave.
        connection1.sendUnsealedData(toBeSealed)
        val unsealReq = sealUnsealRecordingHandler1.pollLastReceivedSealedData
        // Unseal using the 2nd enclave.
        connection2.sendSealedData(unsealReq.getRemainingBytes())
        val unsealed = sealUnsealRecordingHandler2.pollLastReceivedUnsealedData
        // Both encrypted texts should be the same as the default key policy for sealing is MRSIGNER.
        assertEquals(toBeSealed.plaintext, unsealed.plaintext)
    }

    @Test
    fun `seal in one enclave and unseal in another instance of the same enclave (with authenticated data)`() {
        val toBeSealed = PlaintextAndEnvelope(
                plaintext = OpaqueBytes("Sealing Hello World!".toByteArray()),
                authenticatedData = OpaqueBytes("Sealing Hello World Authenticated Data!".toByteArray()))
        val connection1 = testEnclaves.createOrGetEnclave(
                handler = sealUnsealRecordingHandler1,
                enclaveClass = SealUnsealEnclave::class.java,
                keyGenInput = "signerA"
        )
        val connection2 = testEnclaves.createOrGetEnclave(
                handler = sealUnsealRecordingHandler2,
                enclaveClass = SealUnsealEnclave::class.java,
                keyGenInput = "signerA"
        )
        assertNotEquals(connection1, connection2)
        // Seal using the 1st enclave.
        connection1.sendUnsealedData(toBeSealed)
        val unsealReq = sealUnsealRecordingHandler1.pollLastReceivedSealedData
        // Unseal using the 2nd enclave.
        connection2.sendSealedData(unsealReq.getRemainingBytes())
        val unsealed = sealUnsealRecordingHandler2.pollLastReceivedUnsealedData
        assertEquals(toBeSealed.plaintext, unsealed.plaintext)
        assertEquals(toBeSealed.authenticatedData, unsealed.authenticatedData)
    }

    @Test
    fun `seal in one enclave and unseal in another enclave (with authenticated data)`() {
        val toBeSealed = PlaintextAndEnvelope(
                plaintext = OpaqueBytes("Sealing Hello World!".toByteArray()),
                authenticatedData = OpaqueBytes("Sealing Hello World Authenticated Data!".toByteArray()))
        val connection1 = testEnclaves.createOrGetEnclave(
                handler = sealUnsealRecordingHandler1,
                enclaveClass = SealUnsealEnclave::class.java,
                keyGenInput = "signerA"
        )
        val connection2 = testEnclaves.createOrGetEnclave(
                handler = sealUnsealRecordingHandler2,
                enclaveClass = SealUnsealEnclaveAux::class.java, // Distinct enclave measurement.
                keyGenInput = "signerA"
        )
        assertNotEquals(connection1, connection2)
        // Seal using the 1st enclave.
        connection1.sendUnsealedData(toBeSealed)
        // Unseal using the 2nd enclave.
        connection2.sendSealedData(sealUnsealRecordingHandler1.pollLastReceivedSealedData.getRemainingBytes())
        val unsealed = sealUnsealRecordingHandler2.pollLastReceivedUnsealedData
        // Both encrypted texts should be the same as the default key policy for sealing is MRSIGNER.
        assertEquals(toBeSealed.plaintext, unsealed.plaintext)
        assertEquals(toBeSealed.authenticatedData, unsealed.authenticatedData)
    }

    @Test
    fun `seal in one enclave and unseal in another enclave distinct signers`() {
        val toBeSealed = PlaintextAndEnvelope(OpaqueBytes("Sealing Hello World!".toByteArray()))
        // Setup the 1st enclave.
        val rootConnection1 = testEnclaves.createOrGetEnclave(
                handler = throwHandler1,
                enclaveClass = SealUnsealEnclaveExceptionHandler::class.java,
                keyGenInput = "signerA"
        )
        val connection1 = rootConnection1.downstream
        //
        // Setup the 2nd enclave.
        val rootConnection2 = testEnclaves.createOrGetEnclave(
                handler = throwHandler2,
                enclaveClass = SealUnsealEnclaveExceptionHandler::class.java,
                keyGenInput = "signerB"
        )
        assertNotEquals(rootConnection1, rootConnection2)
        val connection2 = rootConnection2.downstream
        //
        // Seal using the 1st enclave.
        connection1.sendUnsealedData(toBeSealed)
        // Unseal using the 2nd enclave. Expect exception with message "SGX_ERROR_MAC_MISMATCH"
        val exception = assertThrows<RuntimeException> {
            connection2.sendSealedData(sealUnsealRecordingHandler1.pollLastReceivedSealedData.getRemainingBytes())
        }
        assertTrue(exception.message!!.contains("SGX_ERROR_MAC_MISMATCH"))
    }
}
