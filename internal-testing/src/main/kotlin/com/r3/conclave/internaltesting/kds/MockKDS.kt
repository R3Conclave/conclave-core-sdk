package com.r3.conclave.internaltesting.kds

import com.r3.conclave.common.EnclaveConstraint
import com.r3.conclave.common.EnclaveInstanceInfo
import com.r3.conclave.common.EnclaveSecurityInfo
import com.r3.conclave.common.kds.MasterKeyType
import com.r3.conclave.enclave.Enclave
import com.r3.conclave.host.internal.createMockHost
import com.r3.conclave.internaltesting.EmbeddedServer
import com.r3.conclave.mail.Curve25519PrivateKey
import com.r3.conclave.utilities.internal.writeData
import com.r3.conclave.utilities.internal.writeIntLengthPrefixString
import com.r3.conclave.utilities.internal.writeShortLengthPrefixBytes
import io.ktor.application.*
import io.ktor.request.*
import io.ktor.response.*
import io.ktor.routing.*
import io.ktor.util.pipeline.*
import kotlinx.serialization.Serializable
import org.junit.jupiter.api.extension.AfterEachCallback
import org.junit.jupiter.api.extension.ExtensionContext
import java.net.URL
import java.security.MessageDigest
import java.util.*

/**
 * A mock KDS for unit testing. The keys provided by this mock service do not match those of a real KDS, including
 * the DEBUG key.
 */
class MockKDS : AutoCloseable, AfterEachCallback {
    private val kdsEnclaveMock = createMockHost(MockKdsEnclave::class.java)
    private val server = EmbeddedServer()
    val url: URL = server.hostUrl

    var checkPolicyConstraint = true
    var privateKeyRequestModifier: PrivateKeyRequestModifier? = null
    var previousPublicKeyRequest: PublicKeyRequest? = null
    var previousPrivateKeyRequest: PrivateKeyRequest? = null

    init {
        kdsEnclaveMock.start(null, null, null) { }
        initServer()
    }

    override fun close() {
        server.stop()
        kdsEnclaveMock.close()
    }

    override fun afterEach(context: ExtensionContext) {
        close()
    }

    val enclaveConstraint: EnclaveConstraint get() {
        return EnclaveConstraint().apply {
            acceptableCodeHashes += kdsEnclaveMock.enclaveInstanceInfo.enclaveInfo.codeHash
            minSecurityLevel = EnclaveSecurityInfo.Summary.INSECURE
        }
    }

    private fun initServer() {
        server.installRoutes {
            post("/public") {
                val httpRequestBody = extractHttpRequestBody<PublicKeyRequest>()
                previousPublicKeyRequest = httpRequestBody
                val httpResponseBody = processPublicKeyRequest(httpRequestBody)
                sendResponse(httpResponseBody)
            }

            post("/private") {
                val httpRequestBody = extractHttpRequestBody<PrivateKeyRequest>()
                previousPrivateKeyRequest = httpRequestBody
                val httpResponseBody = processPrivateKeyRequest(httpRequestBody)
                sendResponse(httpResponseBody)
            }
        }
        server.start()
    }

    private suspend inline fun <reified T : Any> PipelineContext<Unit, ApplicationCall>.extractHttpRequestBody() =
        call.receive<T>()

    private suspend inline fun PipelineContext<Unit, ApplicationCall>.sendResponse(httpResponseBody: Any) {
        call.respond(httpResponseBody)
    }

    private fun applyRequestModifier(request: PrivateKeyRequest): PrivateKeyRequest {
        val privateKeyRequestModifier = privateKeyRequestModifier ?: return request

        val name = privateKeyRequestModifier.name ?: request.name
        val masterKeyType = privateKeyRequestModifier.masterKeyType ?: request.masterKeyType
        val policyConstraint = privateKeyRequestModifier.policyConstraint ?: request.policyConstraint

        return PrivateKeyRequest(request.appAttestationReport, name, masterKeyType, policyConstraint)
    }

    private fun processPrivateKeyRequest(originalRequest: PrivateKeyRequest): PrivateKeyResponse {
        val modifiedRequest = applyRequestModifier(originalRequest)
        val appEnclaveInstanceInfo = EnclaveInstanceInfo.deserialize(
            Base64.getDecoder().decode(modifiedRequest.appAttestationReport)
        )
        if (checkPolicyConstraint) {
            EnclaveConstraint.parse(modifiedRequest.policyConstraint).check(appEnclaveInstanceInfo)
        }
        val mailBytes = generateEncodedMail(modifiedRequest, appEnclaveInstanceInfo)
        return PrivateKeyResponse(kdsEnclaveMock.enclaveInstanceInfo.serialize(), mailBytes)
    }

    private fun processPublicKeyRequest(request: PublicKeyRequest): PublicKeyResponse {
        val privateKey = derivePrivateKey(request)
        val encodedPublicKey = Curve25519PrivateKey(privateKey).publicKey.encoded
        val signature = generatePublicKeySignature(request, encodedPublicKey)
        return PublicKeyResponse(encodedPublicKey, signature, kdsEnclaveMock.enclaveInstanceInfo.serialize())
    }

    private fun derivePrivateKey(keySpec: KdsKeySpec): ByteArray {
        // This isn't the actual derivation algorithm the KDS uses (obviously), but it doesn't matter. This is
        // sufficient to generate unique stable keys per key spec for mock testing. This even means a master key
        // isn't needed.
        val sha256 = MessageDigest.getInstance("SHA-256")
        sha256.update(keySpec.name.encodeToByteArray())
        sha256.update(keySpec.masterKeyType.ordinal.toByte())
        sha256.update(keySpec.policyConstraint.encodeToByteArray())
        return sha256.digest()
    }

    private fun generateEncodedMail(
        privateKeyRequest: PrivateKeyRequest,
        appEnclaveInstanceInfo: EnclaveInstanceInfo
    ): ByteArray {
        val privateKey = derivePrivateKey(privateKeyRequest)
        val envelope = generatePrivateKeyEnvelope(privateKeyRequest)
        return (kdsEnclaveMock.mockEnclave as MockKdsEnclave).encryptMail(appEnclaveInstanceInfo, privateKey, envelope)
    }

    private fun generatePublicKeySignature(keySpec: KdsKeySpec, encodedPublicKey: ByteArray): ByteArray {
        val signingBytes = writeData {
            writeByte(1)
            writeIntLengthPrefixString(keySpec.name)
            writeByte(keySpec.masterKeyType.ordinal)
            writeIntLengthPrefixString(keySpec.policyConstraint)
            writeShortLengthPrefixBytes(encodedPublicKey)
        }
        return (kdsEnclaveMock.mockEnclave as MockKdsEnclave).sign(signingBytes)
    }

    private fun generatePrivateKeyEnvelope(keySpec: KdsKeySpec): ByteArray {
        return writeData {
            writeByte(1)
            writeIntLengthPrefixString(keySpec.name)
            writeByte(keySpec.masterKeyType.ordinal)
            writeIntLengthPrefixString(keySpec.policyConstraint)
        }
    }

    @Serializable
    private class PublicKeyResponse(
        val publicKey: ByteArray,
        val signature: ByteArray,
        val kdsAttestationReport: ByteArray
    )

    @Serializable
    private class PrivateKeyResponse(val kdsAttestationReport: ByteArray, val encryptedPrivateKey: ByteArray)

    private class MockKdsEnclave : Enclave() {
        fun encryptMail(eii: EnclaveInstanceInfo, body: ByteArray, envelope: ByteArray): ByteArray {
            return postOffice(eii).encryptMail(body, envelope)
        }

        fun sign(bytes: ByteArray): ByteArray {
            return with(signer()) {
                update(bytes)
                sign()
            }
        }
    }
}

/**
 * This class allows anyone to change the private key request received by the KDS mocked service as needed. Similar to what a malicious actor would do
 *  It is useful for testing purposes. For instance, to ensure the enclave is doing proper checks
 */
class PrivateKeyRequestModifier(
    val name: String? = null,
    val masterKeyType: MasterKeyType? = null,
    val policyConstraint: String? = null
)
