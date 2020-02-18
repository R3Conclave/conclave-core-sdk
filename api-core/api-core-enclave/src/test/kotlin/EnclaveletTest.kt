import com.r3.sgx.core.common.*
import com.r3.sgx.core.common.attestation.AttestedSignatureVerifier
import com.r3.sgx.core.common.attestation.PublicKeyAttester
import com.r3.sgx.core.common.crypto.SignatureScheme
import com.r3.sgx.core.common.crypto.SignatureSchemeId
import com.r3.sgx.core.enclave.EnclaveApi
import com.r3.sgx.core.enclave.Enclavelet
import com.r3.sgx.core.host.EnclaveletHostHandler
import com.r3.sgx.core.host.EpidAttestationHostConfiguration
import com.r3.sgx.dynamictesting.TestEnclavesBasedTest
import com.r3.sgx.testing.BytesRecordingHandler
import com.r3.sgx.testing.TrustedSgxQuote
import org.junit.Test
import java.nio.ByteBuffer
import java.security.KeyPair
import java.security.MessageDigest
import java.util.function.Consumer

class EnclaveletTest : TestEnclavesBasedTest() {
    class SigningEnclave : Enclavelet() {
        lateinit var scheme: SignatureScheme
        lateinit var keyPair: KeyPair
        override fun createReportData(api: EnclaveApi): ByteCursor<SgxReportData> {
            val reportDataCursor = Cursor.allocate(SgxReportData)
            scheme = api.getSignatureScheme(SignatureSchemeId.EDDSA_ED25519_SHA512)
            keyPair = scheme.generateKeyPair()
            val keyDigest = MessageDigest
                    .getInstance(PublicKeyAttester.DEFAULT_KEY_DIGEST)
                    .digest(keyPair.public.encoded)
            require(keyDigest.size == SgxReportData.size)
            reportDataCursor.getBuffer().put(keyDigest, 0, SgxReportData.size)
            return reportDataCursor
        }

        override fun createHandler(api: EnclaveApi): Handler<*> {
            return SigningHandler(scheme, keyPair)
        }
    }

    class SigningHandler(val scheme: SignatureScheme, val keyPair: KeyPair) : BytesHandler() {
        override fun onReceive(connection: Connection, input: ByteBuffer) {
            val inputBytes = ByteArray(input.remaining()).also {
                input.get(it)
            }
            val signature = scheme.sign(keyPair.private, inputBytes)
            val reply = ByteBuffer.allocate(4 + keyPair.public.encoded.size + 4 + signature.size)
            reply.putInt(keyPair.public.encoded.size)
            reply.put(keyPair.public.encoded)
            reply.putInt(signature.size)
            reply.put(signature)
            reply.rewind()
            connection.send(reply)
        }
    }

    @Test
    fun simpleEnclaveletTest() {
        val attestationConfiguration = EpidAttestationHostConfiguration(
                quoteType = SgxQuoteType32.LINKABLE,
                spid = Cursor.allocate(SgxSpid)
        )
        val handler = EnclaveletHostHandler(attestationConfiguration)
        withEnclaveHandle(handler, SigningEnclave::class.java, block = Consumer { enclaveHandle ->
            val connection = enclaveHandle.connection
            val signedQuoteCursor = connection.attestation.getQuote()

            val enclaveOcalls = BytesRecordingHandler()
            val (_, channel) = connection.channels.addDownstream(enclaveOcalls).get()
            val message = "Hello enclave, I'm the host".toByteArray()
            channel.send(ByteBuffer.wrap(message))

            val reply = enclaveOcalls.nextCall
            val publicKeySize = reply.getInt()
            val publicKey = ByteArray(publicKeySize)
            reply.get(publicKey)
            val signatureSize = reply.getInt()
            val signature = ByteArray(signatureSize)
            reply.get(signature)

            val trustedSgxQuote = TrustedSgxQuote.fromSignedQuote(signedQuoteCursor)
            val enclaveSignatureVerifier = AttestedSignatureVerifier(
                    SignatureSchemeId.EDDSA_ED25519_SHA512,
                    PublicKeyAttester(trustedSgxQuote))
            enclaveSignatureVerifier.verify(
                    attestedPublicKey = enclaveSignatureVerifier.decodeAttestedKey(publicKey),
                    signature = signature,
                    clearData = message
            )
        })
    }
}