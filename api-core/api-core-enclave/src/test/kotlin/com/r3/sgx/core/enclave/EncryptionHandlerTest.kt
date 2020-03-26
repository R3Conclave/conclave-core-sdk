package com.r3.sgx.core.enclave

import com.r3.sgx.core.common.*
import com.r3.sgx.core.common.attestation.AttestedSignatureVerifier
import com.r3.sgx.core.common.attestation.PublicKeyAttester
import com.r3.sgx.core.common.crypto.SignatureScheme
import com.r3.sgx.core.common.crypto.SignatureSchemeId
import com.r3.sgx.core.common.internal.encryption.DecryptorAESGCM
import com.r3.sgx.core.common.internal.encryption.EncryptorAESGCM
import com.r3.sgx.core.host.EnclaveletHostHandler
import com.r3.sgx.core.host.EpidAttestationHostConfiguration
import com.r3.sgx.dynamictesting.EnclaveBuilder
import com.r3.sgx.dynamictesting.TestEnclavesBasedTest
import com.r3.sgx.testing.*
import org.junit.Assert.assertArrayEquals
import org.junit.Test
import org.whispersystems.curve25519.Curve25519
import java.nio.ByteBuffer
import java.security.KeyPair
import java.security.MessageDigest
import java.util.function.Consumer
import javax.crypto.spec.SecretKeySpec
import kotlin.test.assertEquals

class EncryptionHandlerTest: TestEnclavesBasedTest() {

    class EncryptingEchoEnclave: Enclavelet() {
        lateinit var signatureScheme: SignatureScheme
        lateinit var txSigningKeyPair: KeyPair

        override fun createReportData(api: EnclaveApi): ByteCursor<SgxReportData> {
            signatureScheme = api.getSignatureScheme(SignatureSchemeId.EDDSA_ED25519_SHA512)
            txSigningKeyPair = signatureScheme.generateKeyPair()
            val reportData = Cursor.allocate(SgxReportData)
            val buffer = reportData.getBuffer()
            val keyDigest = MessageDigest.getInstance("SHA-512").digest(txSigningKeyPair.public.encoded)
            require(keyDigest.size == SgxReportData.size) {
                "Key Digest of ${keyDigest.size} bytes instead of ${SgxReportData.size}"
            }
            buffer.put(keyDigest, 0, SgxReportData.size)
            return reportData
        }

        override fun createHandler(api: EnclaveApi): Handler<*> {
            return EncryptionRespondingHandler(txSigningKeyPair, signatureScheme, EchoHandler())
        }
    }

    @Test
    fun testEndToEndEncryption() {
        val builder = EnclaveBuilder(includeClasses = listOf(Curve25519::class.java))
        val attestationConfiguration = EpidAttestationHostConfiguration(
                quoteType = SgxQuoteType.LINKABLE,
                spid = Cursor.allocate(SgxSpid)
        )
        val hostHandler = EnclaveletHostHandler(attestationConfiguration)
        withEnclaveHandle(hostHandler, EncryptingEchoEnclave::class.java, builder, Consumer { handle ->
            val connection = handle.connection
            val attestedQuote = TrustedSgxQuote.fromSignedQuote(connection.attestation.getSignedQuote())
            val attestedSigVerifer = AttestedSignatureVerifier(
                    SignatureSchemeId.EDDSA_ED25519_SHA512,
                    PublicKeyAttester(attestedQuote)
            )
            val encryptedHandler = EncryptionInitiatingHandler(
                    attestedSigVerifer,
                    EncryptionProtocolId.ED25519_AESGCM128)
            val (_, encryptedConnection) = handle
                    .connection
                    .channels
                    .addDownstream(encryptedHandler)
                    .get()
            val recorder = StringRecordingHandler()
            val sender = encryptedConnection.initiate(recorder)
            sender.send("Hello")
            assertEquals(1, recorder.calls.size)
            assertEquals("Hello", recorder.calls.last())
            sender.send("Foo")
            assertEquals(2, recorder.calls.size)
            assertEquals("Foo", recorder.calls.last())
        })
    }

    class AESEncryptingEnclave: BytesEnclave() {
        companion object {
            val sharedSecret = SecretKeySpec(ByteArray(32) { 5 }, "AES")
            val encryptor = EncryptorAESGCM(sharedSecret)
        }
        override fun onReceive(api: EnclaveApi, connection: BytesHandler.Connection, bytes: ByteBuffer) {
            encryptor.process(bytes, connection.upstream)
        }
    }

    @Test
    fun testAESEncryption() {
        withEnclaveHandle(RootHandler(), AESEncryptingEnclave::class.java, block = Consumer { enclaveHandle ->
            val handler = BytesRecordingHandler()
            val sender = enclaveHandle.connection.addDownstream(handler)
            val msg = "Hello world".toByteArray()
            sender.send(ByteBuffer.wrap(msg))
            val msg_encoded = handler.nextCall

            val decrypter = DecryptorAESGCM(AESEncryptingEnclave.sharedSecret)
            val msg_decoded = decrypter.process(msg_encoded)
            assertArrayEquals(msg, msg_decoded)
        })
    }

    class AESDecryptingEnclave: BytesEnclave() {
        companion object {
            val sharedSecret = SecretKeySpec(ByteArray(32) { 5 }, "AES")
            val decryptor = DecryptorAESGCM(sharedSecret)
        }
        override fun onReceive(api: EnclaveApi, connection: BytesHandler.Connection, bytes: ByteBuffer) {
            connection.send(ByteBuffer.wrap(decryptor.process(bytes)))
        }
    }

    @Test
    fun testAESDecryption() {
        withEnclaveHandle(RootHandler(), AESDecryptingEnclave::class.java, block = Consumer { enclaveHandle ->
            val handler = BytesRecordingHandler()
            val sender = enclaveHandle.connection.addDownstream(handler)
            val msg = "Hello world".toByteArray()
            val encryptor = EncryptorAESGCM(AESDecryptingEnclave.sharedSecret)
            encryptor.process(ByteBuffer.wrap(msg), sender.upstream)
            val responseBuffer = handler.nextCall
            val msgDecoded = ByteArray(responseBuffer.remaining()).also { responseBuffer.get(it) }
            assertArrayEquals(msg, msgDecoded)
        })
    }
}