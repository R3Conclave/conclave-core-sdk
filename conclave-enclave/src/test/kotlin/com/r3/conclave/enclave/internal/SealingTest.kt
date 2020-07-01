package com.r3.conclave.enclave.internal

import com.r3.conclave.common.EnclaveMode
import com.r3.conclave.common.OpaqueBytes
import com.r3.conclave.common.internal.*
import com.r3.conclave.common.internal.handler.Handler
import com.r3.conclave.common.internal.handler.HandlerConnected
import com.r3.conclave.common.internal.handler.Sender
import com.r3.conclave.dynamictesting.EnclaveBuilder
import com.r3.conclave.dynamictesting.TestEnclaves
import com.r3.conclave.enclave.Enclave
import com.r3.conclave.host.internal.EnclaveHandle
import com.r3.conclave.host.internal.NativeEnclaveHandle
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import java.nio.ByteBuffer
import kotlin.collections.ArrayList

class SealingTest {
    companion object {
        @JvmField
        @RegisterExtension
        val testEnclaves = TestEnclaves()
    }

    private lateinit var enclaveHandle: EnclaveHandle<*>

    @BeforeEach
    fun cleanUp() {
        if (this::enclaveHandle.isInitialized) {
            enclaveHandle.destroy()
        }
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

    class SealUnsealEnclave : InternalEnclave, Enclave() {
        override fun internalInitialise(env: EnclaveEnvironment, upstream: Sender): HandlerConnected<*> {
            return HandlerConnected.connect(SealUnsealEnclaveHandler(env), upstream)
        }
    }

    class SealUnsealEnclaveAux : InternalEnclave, Enclave() {
        override fun internalInitialise(env: EnclaveEnvironment, upstream: Sender): HandlerConnected<*> {
            return HandlerConnected.connect(SealUnsealEnclaveHandler(env), upstream)
        }
    }

    @Test
    fun `get default sealing key`() {
        val handler = GetSealingKeyRecordingHandler()
        val connection = createEnclave(handler, GetSealingKeyEnclave::class.java)
        val keys1 = runKeyRequests(connection, handler)
        val keys2 = runKeyRequests(connection, handler)
        // check key stability
        assertEquals(keys1, keys2)
        //
    }

    @Test
    fun `get default sealing key with 2 instances of the same enclave and signer`() {
        val handler1 = GetSealingKeyRecordingHandler()
        val handler2 = GetSealingKeyRecordingHandler()
        val connection1 = createEnclave(handler1, GetSealingKeyEnclave::class.java)
        val connection2 = createEnclave(handler2, GetSealingKeyEnclave::class.java)
        val keys1 = runKeyRequests(connection1, handler1)
        val keys2 = runKeyRequests(connection2, handler2)
        // check key stability
        assertEquals(keys1, keys2)
        //
    }

    @Test
    fun `get default sealing key with 2 instances of distinct enclaves but same signer`() {
        val handler1 = GetSealingKeyRecordingHandler()
        val handler2 = GetSealingKeyRecordingHandler()
        val connection1 = createEnclave(handler1, GetSealingKeyEnclave::class.java)
        val connection2 = createEnclave(handler2, GetSealingKeyEnclaveAux::class.java)
        val keys1 = runKeyRequests(connection1, handler1)
        val keys2 = runKeyRequests(connection2, handler2)
        assertNotEquals(keys1.report, keys2.report) // from MRENCLAVE should be distinct
        assertNotEquals(keys1.sealMREnclave, keys2.sealMREnclave) // from MRENCLAVE should be distinct
        assertEquals(keys1.sealMRSigner, keys2.sealMRSigner) // from MRSIGNER should be equal
    }

    @Test
    fun `get default sealing key with 2 instances of the same enclave but distinct signers`() {
        val handler1 = GetSealingKeyRecordingHandler()
        val handler2 = GetSealingKeyRecordingHandler()
        val connection1 = createEnclave(handler1, GetSealingKeyEnclave::class.java, keyGenInput = "enclave1")
        val connection2 = createEnclave(handler2, GetSealingKeyEnclave::class.java, keyGenInput = "enclave2")
        val keys1 = runKeyRequests(connection1, handler1)
        val keys2 = runKeyRequests(connection2, handler2)
        assertEquals(keys1.report, keys2.report) // from MRENCLAVE should be equal
        assertEquals(keys1.sealMREnclave, keys2.sealMREnclave) // from MRENCLAVE should be equal
        assertNotEquals(keys1.sealMRSigner, keys2.sealMRSigner) // from MRSIGNER should be distinct
    }

    @Test
    fun `get default sealing key with 2 instances of distinct enclaves and distinct signers`() {
        val handler1 = GetSealingKeyRecordingHandler()
        val handler2 = GetSealingKeyRecordingHandler()
        val connection1 = createEnclave(handler1, GetSealingKeyEnclave::class.java, keyGenInput = "enclave1")
        val connection2 = createEnclave(handler2, GetSealingKeyEnclaveAux::class.java, keyGenInput = "enclave2")
        val keys1 = runKeyRequests(connection1, handler1)
        val keys2 = runKeyRequests(connection2, handler2)
        assertNotEquals(keys1.report, keys2.report) // from MRENCLAVE should be distinct
        assertNotEquals(keys1.sealMREnclave, keys2.sealMREnclave) // from MRENCLAVE should be distinct
        assertNotEquals(keys1.sealMRSigner, keys2.sealMRSigner) // from MRSIGNER should be distinct
    }


    @Test
    fun `seal and unseal data`() {
        val handler = SealUnsealRecordingHandler()
        val connection = createEnclave(handler, SealUnsealEnclave::class.java)
        val toBeSealed = PlaintextAndEnvelope(OpaqueBytes("Sealing Hello World!".toByteArray()))
        connection.sendUnsealedData(toBeSealed) // seal request
        connection.sendSealedData(handler.pollLastReceivedSealedData.getRemainingBytes()) // unseal request
        val unsealed = handler.pollLastReceivedUnsealedData// unsealed data
        assertEquals(toBeSealed.plaintext, unsealed.plaintext)
    }

    @Test
    fun `seal in one enclave and unseal in another instance of the same enclave`() {
        val toBeSealed = PlaintextAndEnvelope(OpaqueBytes("Sealing Hello World!".toByteArray()))
        val handler1 = SealUnsealRecordingHandler()
        val connection1 = createEnclave(handler1, SealUnsealEnclave::class.java)
        val handler2 = SealUnsealRecordingHandler()
        val connection2 = createEnclave(handler2, SealUnsealEnclave::class.java)
        // seal using the 1st enclave
        connection1.sendUnsealedData(toBeSealed)
        val unsealReq = handler1.pollLastReceivedSealedData
        // unseal using the 2nd enclave
        connection2.sendSealedData(sealedData = unsealReq.getRemainingBytes())
        val unsealed = handler2.pollLastReceivedUnsealedData
        assertEquals(toBeSealed.plaintext, unsealed.plaintext)
    }

    @Test
    fun `seal in one enclave and unseal in another enclave`() {
        val toBeSealed = PlaintextAndEnvelope(OpaqueBytes("Sealing Hello World!".toByteArray()))
        val handler1 = SealUnsealRecordingHandler()
        val connection1 = createEnclave(handler1, SealUnsealEnclave::class.java)
        val handler2 = SealUnsealRecordingHandler()
        val connection2 = createEnclave(handler2, /*distinct enclave*/ SealUnsealEnclaveAux::class.java)
        // seal using the 1st enclave
        connection1.sendUnsealedData(toBeSealed)
        val unsealReq = handler1.pollLastReceivedSealedData
        // unseal using the 2nd enclave
        connection2.sendSealedData(unsealReq.getRemainingBytes())
        val unsealed = handler2.pollLastReceivedUnsealedData
        // both encrypted texts should be the same as the default key policy for sealing is MRSIGNER
        assertEquals(toBeSealed.plaintext, unsealed.plaintext)
    }

    @Test
    fun `seal in one enclave and unseal in another instance of the same enclave (with authenticated data)`() {
        val toBeSealed = PlaintextAndEnvelope(
                plaintext = OpaqueBytes("Sealing Hello World!".toByteArray()),
                authenticatedData = OpaqueBytes("Sealing Hello World Authenticated Data!".toByteArray()))
        val handler1 = SealUnsealRecordingHandler()
        val connection1 = createEnclave(handler1, SealUnsealEnclave::class.java)
        val handler2 = SealUnsealRecordingHandler()
        val connection2 = createEnclave(handler2, SealUnsealEnclave::class.java)
        // seal using the 1st enclave
        connection1.sendUnsealedData(toBeSealed)
        val unsealReq = handler1.pollLastReceivedSealedData
        // unseal using the 2nd enclave
        connection2.sendSealedData(unsealReq.getRemainingBytes())
        val unsealed = handler2.pollLastReceivedUnsealedData
        assertEquals(toBeSealed.plaintext, unsealed.plaintext)
        assertEquals(toBeSealed.authenticatedData, unsealed.authenticatedData)
    }

    @Test
    fun `seal in one enclave and unseal in another enclave (with authenticated data)`() {
        val toBeSealed = PlaintextAndEnvelope(
                plaintext = OpaqueBytes("Sealing Hello World!".toByteArray()),
                authenticatedData = OpaqueBytes("Sealing Hello World Authenticated Data!".toByteArray()))
        val handler1 = SealUnsealRecordingHandler()
        val connection1 = createEnclave(handler1, SealUnsealEnclave::class.java)
        val handler2 = SealUnsealRecordingHandler()
        val connection2 = createEnclave(handler2, /*distinct enclave*/ SealUnsealEnclaveAux::class.java)
        // seal using the 1st enclave
        connection1.sendUnsealedData(toBeSealed)
        // unseal using the 2nd enclave
        connection2.sendSealedData(handler1.pollLastReceivedSealedData.getRemainingBytes())
        val unsealed = handler2.pollLastReceivedUnsealedData
        // both encrypted texts should be the same as the default key policy for sealing is MRSIGNER
        assertEquals(toBeSealed.plaintext, unsealed.plaintext)
        assertEquals(toBeSealed.authenticatedData, unsealed.authenticatedData)
    }

    /**
     * The following test was intentionally disabled, as distinct SIGNERS would make the enclave crash
     *
     **/
    @Disabled("https://r3-cev.atlassian.net/browse/CON-106")
    @Test
    fun `seal in one enclave and unseal in another enclave distinct signers`() {
        val toBeSealed = PlaintextAndEnvelope(OpaqueBytes("Sealing Hello World!".toByteArray()))
        val handler1 = SealUnsealRecordingHandler()
        val connection1 = createEnclave(handler1, SealUnsealEnclave::class.java)
        val handler2 = SealUnsealRecordingHandler()
        val connection2 = createEnclave(handler2, SealUnsealEnclaveAux::class.java, keyGenInput = "enclave2")
        // seal using the 1st enclave
        connection1.sendUnsealedData(toBeSealed)
        // unseal using the 2nd enclave
        connection2.sendSealedData(handler1.pollLastReceivedSealedData.getRemainingBytes())
        // both encrypted texts should be the same as the default key policy for sealing is MRSIGNER
        assertEquals(toBeSealed.plaintext, handler2.pollLastReceivedUnsealedData.plaintext)
    }

    private object KeyTypes {
        val requestReportKey = KeyRequest(keyType = KeyType.REPORT, useSigner = false)
        val requestSealMRSignerKey = KeyRequest(keyType = KeyType.SEAL, useSigner = true)
        val requestSealMREnclaveKey = KeyRequest(keyType = KeyType.SEAL, useSigner = false)
    }

    private data class Keys (
            val report: ByteBuffer,
            val sealMRSigner: ByteBuffer,
            val sealMREnclave: ByteBuffer
    )

    /**
     * Helper function to retrieve common SGX keys and tests.
     * @connection enclave to be used in the request.
     * @handler enclave handler.
     * @keyRequests requests to be performed,
     *  default = requestReportKey, requestSealMRSignerKey, requestSealMREnclaveKey
     * @return ArrayList<ByteBuffer> containing the requested keys.
     */
    private fun runKeyRequests(
            connection: GetSealingKeySender,
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
            connection.sendRequest(request)
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

    private fun <CONNECTION> createEnclave(
            handler: Handler<CONNECTION>,
            enclaveClass: Class<out Enclave>,
            enclaveBuilder: EnclaveBuilder = EnclaveBuilder(),
            keyGenInput: String? = null
    ): CONNECTION {
        val enclaveFile = testEnclaves.getSignedEnclaveFile(enclaveClass, enclaveBuilder, keyGenInput).toPath()
        return NativeEnclaveHandle(EnclaveMode.SIMULATION, enclaveFile, false, enclaveClass.name, handler).let {
            enclaveHandle = it
            it.connection
        }
    }
}
