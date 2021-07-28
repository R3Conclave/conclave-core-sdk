package com.r3.conclave.integrationtests.general.tests

import com.r3.conclave.common.EnclaveMode
import com.r3.conclave.common.OpaqueBytes
import com.r3.conclave.common.internal.PlaintextAndEnvelope
import com.r3.conclave.host.EnclaveHost
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

private const val originalEnclave = "com.r3.conclave.integrationtests.general.enclave.SealUnsealEnclave1"
private const val anotherInstanceOriginalEnclave = originalEnclave
private const val anotherEnclaveSameSigner = "com.r3.conclave.integrationtests.general.enclave.SealUnsealEnclaveSameSigner"
private const val anotherEnclaveDistinctSigner = "com.r3.conclave.integrationtests.general.enclave.SealUnsealEnclaveDifferentSigner"

val unsealedMessageBefore = PlaintextAndEnvelope(OpaqueBytes("Sealing Hello World!".toByteArray()), null)
val unsealedMessageAuthenticatedBefore = PlaintextAndEnvelope(OpaqueBytes("Sealing Hello World!".toByteArray()), OpaqueBytes("Sealing Hello World Authenticated Data!".toByteArray()))
val sealingRequest  = byteArrayOf(1)
val unsealingRequest = byteArrayOf(2)

enum class MessageType {
    PLAIN,
    AUTHENTICATED
}

class SealingTest : JvmTest(originalEnclave) {

    @Test
    fun `seal and unseal data`() {
        val sealedMessage = sealMessageInOriginalEnclave(MessageType.PLAIN)
        val unsealedMessageAfterBytes = enclaveHost.callEnclave(unsealingRequest + sealedMessage!!)
        val unsealedMessageAfter = unsealedMessageAfterBytes!!.toPlaintextAndEnvelop()
        assertEquals(unsealedMessageAfter, unsealedMessageBefore)
    }


    @Test
    fun `seal in one enclave and unseal in another instance of the same enclave`() {
        val sealedMessage = sealMessageInOriginalEnclave(MessageType.PLAIN)
        val unsealedMessageAfter = unsealMessageInEnclave(anotherInstanceOriginalEnclave, sealedMessage!!)

        assertEquals(unsealedMessageAfter, unsealedMessageBefore)
    }

    @Test
    fun `seal in one enclave and unseal in another enclave`() {
        val sealedMessage = sealMessageInOriginalEnclave(MessageType.PLAIN)
        val unsealedMessageAfter = unsealMessageInEnclave(anotherEnclaveSameSigner, sealedMessage!!)

        assertEquals(unsealedMessageAfter, unsealedMessageBefore)
    }


    @Test
    fun `seal in one enclave and unseal in another instance of the same enclave (with authenticated data)`() {
        val sealedMessage = sealMessageInOriginalEnclave(MessageType.AUTHENTICATED)
        val unsealedMessageAfter = unsealMessageInEnclave(anotherInstanceOriginalEnclave, sealedMessage!!)

        assertEquals(unsealedMessageAuthenticatedBefore.plaintext, unsealedMessageAfter.plaintext)
        assertEquals(unsealedMessageAuthenticatedBefore.authenticatedData, unsealedMessageAfter.authenticatedData)
    }

    @Test
    fun `seal in one enclave and unseal in another enclave (with authenticated data)`() {
        val sealedMessage = sealMessageInOriginalEnclave(MessageType.AUTHENTICATED)
        val unsealedMessageAfter = unsealMessageInEnclave(anotherEnclaveSameSigner, sealedMessage!!)

        assertEquals(unsealedMessageAuthenticatedBefore.plaintext, unsealedMessageAfter.plaintext)
        assertEquals(unsealedMessageAuthenticatedBefore.authenticatedData, unsealedMessageAfter.authenticatedData)
    }

    @Test
    fun `seal in one enclave and unseal in another enclave distinct signers`() {
        val sealedMessage = sealMessageInOriginalEnclave(MessageType.AUTHENTICATED)
        val exception = assertThrows<RuntimeException> {
             unsealMessageInEnclave(anotherEnclaveDistinctSigner, sealedMessage!!)
        }

        assertTrue(exception.message!!.contains("SGX_ERROR_MAC_MISMATCH"))
    }

    private fun initExtraEnclave(name : String): EnclaveHost {
        val newEnclave = EnclaveHost.load(name)
        val attestationParameters = when(newEnclave.enclaveMode){
            EnclaveMode.RELEASE, EnclaveMode.DEBUG -> getHardwareAttestationParams()
            else -> null
        }
        newEnclave.start(attestationParameters) {
        }

        return newEnclave
    }

    private fun ByteArray.toPlaintextAndEnvelop(): PlaintextAndEnvelope {
        val plainTextSize = this[0].toInt()
        val authenticatedDataSize = this[1].toInt()

        val plainText = this.sliceArray(2 until plainTextSize + 2)
        val authenticatedData = if (authenticatedDataSize != 0) this.sliceArray(plainTextSize + 2 until this.size) else null
        return PlaintextAndEnvelope(OpaqueBytes(plainText), authenticatedData?.let { OpaqueBytes(it) })
    }

    private fun PlaintextAndEnvelope.toByteArray(): ByteArray {
        val plainTextBytes = this.plaintext.bytes
        val plainTextSize = this.plaintext.bytes.size
        val authenticatedDataBytes = this.authenticatedData?.bytes
        val authenticatedDataSize = (this.authenticatedData?.bytes?.size) ?: 0

        val returnArray = ByteArray(2 + plainTextSize + authenticatedDataSize)
        returnArray[0] = plainTextSize.toByte()
        returnArray[1] = authenticatedDataSize.toByte()
        plainTextBytes.copyInto(returnArray, 2)
        authenticatedDataBytes?.copyInto(returnArray, 2 + plainTextSize.toByte())
        return returnArray
    }

    private fun sealMessageInOriginalEnclave(messageType: MessageType = MessageType.PLAIN): ByteArray? {
        return when(messageType)
        {
            MessageType.PLAIN->enclaveHost.callEnclave(sealingRequest + unsealedMessageBefore.toByteArray())
            MessageType.AUTHENTICATED->enclaveHost.callEnclave(sealingRequest + unsealedMessageAuthenticatedBefore.toByteArray())
        }
    }

    private fun unsealMessageInEnclave(enclaveName: String, sealedMessage: ByteArray): PlaintextAndEnvelope {
        val enclave = initExtraEnclave(enclaveName)
        val unsealedMessageBytes = enclave.callEnclave(unsealingRequest + sealedMessage)
        enclave.close()
        return unsealedMessageBytes!!.toPlaintextAndEnvelop()
    }
}
