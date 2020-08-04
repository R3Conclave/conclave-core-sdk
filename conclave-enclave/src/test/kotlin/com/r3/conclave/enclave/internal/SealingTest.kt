package com.r3.conclave.enclave.internal

import com.r3.conclave.common.OpaqueBytes
import com.r3.conclave.common.internal.KeyType
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
import java.nio.ByteBuffer

class SealingTest {
    companion object {
        @JvmField
        @RegisterExtension
        val testEnclaves = TestEnclaves()

        // Reusable handlers for sealing key tests.
        val getSealingKeyRecordingHandler1 = GetSealingKeyRecordingHandler()
        val getSealingKeyRecordingHandler2 = GetSealingKeyRecordingHandler()
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

    private object KeyTypes {
        val requestReportKey = KeyRequest(keyType = KeyType.REPORT, useSigner = false)
        val requestSealMRSignerKey = KeyRequest(keyType = KeyType.SEAL, useSigner = true)
        val requestSealMREnclaveKey = KeyRequest(keyType = KeyType.SEAL, useSigner = false)
    }

    private data class Keys(
            val report: ByteBuffer,
            val sealMRSigner: ByteBuffer,
            val sealMREnclave: ByteBuffer
    )

    /**
     * Helper function to retrieve common SGX keys and tests.
     * @param handler enclave handler.
     * @param keyRequests requests to be performed,
     *  default = requestReportKey, requestSealMRSignerKey, requestSealMREnclaveKey
     * @return ArrayList<ByteBuffer> containing the requested keys.
     */
    private fun GetSealingKeySender.runKeyRequests(
            handler: GetSealingKeyRecordingHandler,
            keyRequests: ArrayList<KeyRequest> = ArrayList(
                    listOf(
                            KeyTypes.requestReportKey,
                            KeyTypes.requestSealMRSignerKey,
                            KeyTypes.requestSealMREnclaveKey)
            )
    ): Keys {
        // populate array with generated keys
        val keyList = ArrayList<ByteBuffer>()
        var reportKey = ByteBuffer.wrap(ByteArray(0))
        var sealMRSignerKey = ByteBuffer.wrap(ByteArray(0))
        var sealMREnclaveKey = ByteBuffer.wrap(ByteArray(0))
        for (request in keyRequests) {
            this.sendRequest(request)
            keyList.add(handler.nextCall.duplicate())
            when (request) {
                KeyTypes.requestReportKey -> reportKey = keyList.last()
                KeyTypes.requestSealMRSignerKey -> sealMRSignerKey = keyList.last()
                KeyTypes.requestSealMREnclaveKey -> sealMREnclaveKey = keyList.last()
            }
        }
        assertEquals(keyList.size, keyRequests.size)
        val emptyKey = ByteBuffer.wrap(ByteArray(16))
        // check if the keys are not empty and with the expected size
        for (key in keyList) {
            assertEquals(key.capacity(), 16)
            assertNotEquals(key, emptyKey)
        }
        // check if all keys generated are distinct from each other
        val keyListUnique = keyList.distinct()
        assertEquals(keyList.size, keyListUnique.size)
        //
        return Keys(reportKey, sealMRSignerKey, sealMREnclaveKey)
    }

    class GetSealingKeyEnclaveHandler(private val env: EnclaveEnvironment) : GetSealingKeyHandler() {
        override fun onReceive(connection: GetSealingKeySender, keyRequest: KeyRequest) {
            connection.sendResponse(env.defaultSealingKey(
                    keyType = keyRequest.keyType,
                    useSigner = keyRequest.useSigner)
            )
        }
    }

    class GetSealingKeyEnclave : InternalEnclave, Enclave() {
        override fun internalInitialise(env: EnclaveEnvironment, upstream: Sender): HandlerConnected<*> {
            return HandlerConnected.connect(GetSealingKeyEnclaveHandler(env), upstream)
        }
    }

    class GetSealingKeyEnclaveAux : InternalEnclave, Enclave() {
        override fun internalInitialise(env: EnclaveEnvironment, upstream: Sender): HandlerConnected<*> {
            return HandlerConnected.connect(GetSealingKeyEnclaveHandler(env), upstream)
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
    fun `get default sealing key`() {
        val connection = testEnclaves.createOrGetEnclave(
                handler = getSealingKeyRecordingHandler1,
                enclaveClass = GetSealingKeyEnclave::class.java,
                keyGenInput = "signerA"
        )
        val keys1 = connection.runKeyRequests(getSealingKeyRecordingHandler1)
        val keys2 = connection.runKeyRequests(getSealingKeyRecordingHandler1)
        // Check key stability.
        assertEquals(keys1, keys2)
        //
    }

    @Test
    fun `get default sealing key with 2 instances of the same enclave and signer`() {
        val connection1 = testEnclaves.createOrGetEnclave(
                handler = getSealingKeyRecordingHandler1,
                enclaveClass = GetSealingKeyEnclave::class.java,
                keyGenInput = "signerA"
        )
        val connection2 = testEnclaves.createOrGetEnclave(
                handler = getSealingKeyRecordingHandler2,
                enclaveClass = GetSealingKeyEnclave::class.java,
                keyGenInput = "signerA"
        )
        assertNotEquals(connection1, connection2)
        val keys1 = connection1.runKeyRequests(getSealingKeyRecordingHandler1)
        val keys2 = connection2.runKeyRequests(getSealingKeyRecordingHandler2)
        // Check key stability.
        assertEquals(keys1, keys2)
        //
    }

    @Test
    fun `get default sealing key with 2 instances of distinct enclaves but same signer`() {
        val connection1 = testEnclaves.createOrGetEnclave(
                handler = getSealingKeyRecordingHandler1,
                enclaveClass = GetSealingKeyEnclave::class.java,
                keyGenInput = "signerA"
        )
        val connection2 = testEnclaves.createOrGetEnclave(
                handler = getSealingKeyRecordingHandler2,
                enclaveClass = GetSealingKeyEnclaveAux::class.java,
                keyGenInput = "signerA"
        )
        assertNotEquals(connection1, connection2)
        val keys1 = connection1.runKeyRequests(getSealingKeyRecordingHandler1)
        val keys2 = connection2.runKeyRequests(getSealingKeyRecordingHandler2)
        assertNotEquals(keys1.report, keys2.report) // From MRENCLAVE should be distinct.
        assertNotEquals(keys1.sealMREnclave, keys2.sealMREnclave) // From MRENCLAVE should be distinct.
        assertEquals(keys1.sealMRSigner, keys2.sealMRSigner) // From MRSIGNER should be equal.
    }

    @Test
    fun `get default sealing key with 2 instances of the same enclave but distinct signers`() {
        val connection1 = testEnclaves.createOrGetEnclave(
                handler = getSealingKeyRecordingHandler1,
                enclaveClass = GetSealingKeyEnclave::class.java,
                keyGenInput = "signerA"
        )
        val connection2 = testEnclaves.createOrGetEnclave(
                handler = getSealingKeyRecordingHandler2,
                enclaveClass = GetSealingKeyEnclave::class.java,
                keyGenInput = "signerB"
        )
        assertNotEquals(connection1, connection2)
        val keys1 = connection1.runKeyRequests(getSealingKeyRecordingHandler1)
        val keys2 = connection2.runKeyRequests(getSealingKeyRecordingHandler2)
        assertEquals(keys1.report, keys2.report) // From MRENCLAVE should be equal.
        assertEquals(keys1.sealMREnclave, keys2.sealMREnclave) // From MRENCLAVE should be equal.
        assertNotEquals(keys1.sealMRSigner, keys2.sealMRSigner) // From MRSIGNER should be distinct.
    }

    @Test
    fun `get default sealing key with 2 instances of distinct enclaves and distinct signers`() {
        val connection1 = testEnclaves.createOrGetEnclave(
                handler = getSealingKeyRecordingHandler1,
                enclaveClass = GetSealingKeyEnclave::class.java,
                keyGenInput = "signerA"
        )
        val connection2 = testEnclaves.createOrGetEnclave(
                handler = getSealingKeyRecordingHandler2,
                enclaveClass = GetSealingKeyEnclaveAux::class.java,
                keyGenInput = "signerB"
        )
        assertNotEquals(connection1, connection2)
        val keys1 = connection1.runKeyRequests(getSealingKeyRecordingHandler1)
        val keys2 = connection2.runKeyRequests(getSealingKeyRecordingHandler2)
        assertNotEquals(keys1.report, keys2.report) // From MRENCLAVE should be distinct.
        assertNotEquals(keys1.sealMREnclave, keys2.sealMREnclave) // From MRENCLAVE should be distinct.
        assertNotEquals(keys1.sealMRSigner, keys2.sealMRSigner) // From MRSIGNER should be distinct.
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
