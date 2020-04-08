package com.r3.sgx.enclave.signing

import com.r3.conclave.common.internal.ByteCursor
import com.r3.conclave.common.internal.Cursor
import com.r3.conclave.common.internal.SgxReportData
import com.r3.conclave.common.internal.SignatureScheme
import com.r3.sgx.core.common.BytesHandler
import com.r3.sgx.core.common.EncryptionRespondingHandler
import com.r3.sgx.core.common.Handler
import com.r3.sgx.core.common.attestation.PublicKeyAttester
import com.r3.sgx.core.common.crypto.SignatureSchemeId
import com.r3.sgx.core.enclave.EnclaveApi
import com.r3.sgx.core.enclave.Enclavelet
import com.r3.sgx.enclave.signing.internal.MyAMQPSerializationScheme
import com.r3.sgx.enclave.signing.internal.asContextEnv
import net.corda.core.serialization.CordaSerializable
import net.corda.core.serialization.SerializedBytes
import net.corda.core.serialization.deserialize
import net.corda.core.serialization.serialize
import java.nio.ByteBuffer
import java.security.KeyPair
import java.security.MessageDigest

@CordaSerializable
data class Stuff(val x: Int, val y: Double, val z: List<Int>)

@CordaSerializable
data class SignedStuff(val stuff: Stuff, val key: ByteArray, val signature: ByteArray) {
    override fun equals(other: Any?): Boolean {
        return (this === other) || (other is SignedStuff
                && stuff == other.stuff
                && key.contentEquals(other.key)
                && signature.contentEquals(other.signature))
    }

    override fun hashCode(): Int = (stuff.hashCode()*31 + key.contentHashCode())*31 + signature.contentHashCode()
}

/**
 * An example enclave receiving and signing data from client
 */
class SigningEnclave : Enclavelet() {

    lateinit var signatureScheme: SignatureScheme
    lateinit var txSigningKeyPair: KeyPair
    val amqpSerializationEnv = MyAMQPSerializationScheme.createSerializationEnv()

    companion object {
        fun getSignatureScheme(api: EnclaveApi): SignatureScheme {
            return api.getSignatureScheme(SignatureSchemeId.EDDSA_ED25519_SHA512)
        }
    }

    override fun createReportData(api: EnclaveApi): ByteCursor<SgxReportData> {
        signatureScheme = getSignatureScheme(api)
        txSigningKeyPair = signatureScheme.generateKeyPair()
        val reportData = Cursor.allocate(SgxReportData)
        val buffer = reportData.getBuffer()
        val keyDigest = MessageDigest
                .getInstance(PublicKeyAttester.DEFAULT_KEY_DIGEST)
                .digest(txSigningKeyPair.public.encoded)
        require(keyDigest.size == SgxReportData.size) {
            "Key Digest of ${keyDigest.size} bytes instead of ${SgxReportData.size}"
        }
        buffer.put(keyDigest, 0, SgxReportData.size)
        return reportData
    }

    override fun createHandler(api: EnclaveApi): Handler<*> {
        return EncryptionRespondingHandler(
                authKeyPair = txSigningKeyPair,
                authSignatureScheme = signatureScheme,
                downstream = SigningHandler()
        )
    }

    inner class SigningHandler : BytesHandler() {
        override fun onReceive(connection: Connection, input: ByteBuffer) {
            val inputBytes = ByteArray(input.remaining()).also {
                input.get(it)
            }

            val deserialized = amqpSerializationEnv.asContextEnv {
                val serializedList = SerializedBytes<Stuff>(inputBytes)
                serializedList.deserialize()
            }

            println("Received Stuff(${deserialized.x},${deserialized.y}, [${deserialized.z.joinToString(",")}])")

            // Sign serialized input message
            val signature = signatureScheme.sign(txSigningKeyPair.private, inputBytes)

            // Serialize response object and send it back
            connection.send(ByteBuffer.wrap(amqpSerializationEnv.asContextEnv {
                SignedStuff(
                        stuff = deserialized,
                        key = txSigningKeyPair.public.encoded,
                        signature = signature)
                        .serialize()
            }.bytes))
        }
    }
}
