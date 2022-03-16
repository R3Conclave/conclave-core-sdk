package com.r3.conclave.internaltesting.kds

import com.r3.conclave.common.EnclaveInstanceInfo
import com.r3.conclave.common.kds.MasterKeyType
import com.r3.conclave.enclave.Enclave
import com.r3.conclave.internaltesting.kds.api.request.PrivateKeyRequest
import com.r3.conclave.internaltesting.kds.api.request.PublicKeyRequest
import com.r3.conclave.internaltesting.kds.api.response.PrivateKeyResponseBody
import com.r3.conclave.internaltesting.kds.api.response.PublicKeyResponseBody
import com.r3.conclave.internaltesting.kds.internal.EmbeddedServer
import com.r3.conclave.host.EnclaveHost
import com.r3.conclave.host.internal.createMockHost
import com.r3.conclave.internaltesting.kds.api.request.EnclaveRequest
import com.r3.conclave.internaltesting.kds.api.request.KeySpec
import com.r3.conclave.internaltesting.kds.internal.enclave.api.EnclaveResponse
import com.r3.conclave.mail.Curve25519PrivateKey
import com.r3.conclave.mail.internal.noise.protocol.Noise
import com.r3.conclave.utilities.internal.writeData
import com.r3.conclave.utilities.internal.writeIntLengthPrefixBytes
import com.r3.conclave.utilities.internal.writeIntLengthPrefixString
import com.r3.conclave.utilities.internal.writeShortLengthPrefixBytes
import io.ktor.application.*
import io.ktor.request.*
import io.ktor.response.*
import io.ktor.routing.*
import io.ktor.util.*
import io.ktor.util.pipeline.*
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.security.KeyPair
import java.util.*

@OptIn(InternalAPI::class)
class KDSServiceMock() : AutoCloseable {

    /** This class allows anyone to change the private key request received by the KDS mocked service as needed. Similar to what a malicious actor would do
     *  It is useful for testing purposes. For instance, to ensure the enclave is doing proper checks
     */
    class PrivateKeyRequestModifier(
        var name: String? = null,
        val masterKeyType: MasterKeyType? = null,
        val policyConstraint: String? = null
    )

    private var privateKeyRequestModifier: PrivateKeyRequestModifier? = null
    private val kdsEnclaveMock = createMockHost(KDSEnclaveMock::class.java)
    private val server = EmbeddedServer()
    val hostUrl = server.hostUrl

    init {
        kdsEnclaveMock.start(null, null, null) { }
        initServer()
    }

    override fun close() {
        server.stop()
        kdsEnclaveMock.close()
    }

    fun addRequestModifier(modifier: PrivateKeyRequestModifier) {
        privateKeyRequestModifier = modifier
    }

    private fun initServer() {
        server.installRoutes {
            post("/public") {
                val httpRequestBody = extractHttpRequestBody<PublicKeyRequest>()
                val httpResponseBody = sendRequestToEnclave(httpRequestBody)
                sendResponse(httpResponseBody)
            }

            post("/private") {
                val httpRequestBody = extractHttpRequestBody<PrivateKeyRequest>()
                val httpResponseBody = sendRequestToEnclave(httpRequestBody)
                sendResponse(httpResponseBody)
            }
        }
        server.start()
    }

    private suspend inline fun <reified T : Any> PipelineContext<Unit, ApplicationCall>.extractHttpRequestBody() =
        call.receive<T>()

    private suspend inline fun PipelineContext<Unit, ApplicationCall>.sendResponse(httpResponseBody: Any) {
        call.response.headers.append("Api-version", "1")
        call.respond(httpResponseBody)
    }

    private fun sendRequestToEnclave(request: PublicKeyRequest): PublicKeyResponseBody {
        val enclaveRequest = createEnclaveRequest(request)
        val enclaveResponse = sendRequestToEnclave(enclaveRequest) as EnclaveResponse.PublicKey
        return createHttpResponseBody(enclaveResponse)
    }

    private fun sendRequestToEnclave(request: PrivateKeyRequest): PrivateKeyResponseBody {
        val modifiedRequest = applyRequestModifier(request)
        val enclaveRequest = createEnclaveRequest(modifiedRequest)
        val enclaveResponse = sendRequestToEnclave(enclaveRequest) as EnclaveResponse.PrivateKey
        return createHttpResponseBody(enclaveResponse)
    }

    private fun sendRequestToEnclave(request: EnclaveRequest): EnclaveResponse =
        EnclaveResponse.deserialize(kdsEnclaveMock.callEnclave(request)!!)

    private fun EnclaveHost.callEnclave(request: EnclaveRequest): ByteArray? =
        kdsEnclaveMock.callEnclave(request.serialize())

    private fun createEnclaveRequest(request: PublicKeyRequest): EnclaveRequest =
        EnclaveRequest.PublicKey(
            KeySpec(
                request.name,
                request.masterKeyType,
                request.policyConstraint
            )
        )

    private fun createEnclaveRequest(request: PrivateKeyRequest): EnclaveRequest =
        EnclaveRequest.PrivateKey(
            request.appAttestationReport.decodeBase64Bytes(),
            KeySpec(request.name, request.masterKeyType, request.policyConstraint)
        )

    private fun createHttpResponseBody(enclaveResponse: EnclaveResponse.PublicKey): PublicKeyResponseBody =
        PublicKeyResponseBody(
            enclaveResponse.publicKey.encodeBase64(),
            enclaveResponse.signature.encodeBase64(),
            enclaveResponse.kdsAttestationReport
        )

    private fun createHttpResponseBody(enclaveResponse: EnclaveResponse.PrivateKey): PrivateKeyResponseBody =
        PrivateKeyResponseBody(enclaveResponse.kdsAttestationReport.encodeBase64(), enclaveResponse.encryptedPrivateKey)

    private fun applyRequestModifier(request: PrivateKeyRequest): PrivateKeyRequest {
        if (privateKeyRequestModifier == null)
            return request

        val privateKeyRequestModifier = privateKeyRequestModifier!!

        val name = privateKeyRequestModifier.name ?: request.name
        val masterKeyType = privateKeyRequestModifier.masterKeyType ?: request.masterKeyType
        val policyConstraint = privateKeyRequestModifier.policyConstraint ?: request.policyConstraint

        return PrivateKeyRequest(request.appAttestationReport, name, masterKeyType, policyConstraint)
    }
}

@OptIn(InternalAPI::class)
private class KDSEnclaveMock : Enclave() {
    lateinit var encryptionKeyPair: KeyPair

    override fun onStartup() {
        initEncryptionKeyPair()
    }

    private fun initEncryptionKeyPair() {
        val sessionKey = ByteArray(32).also(Noise::random)
        val private = Curve25519PrivateKey(sessionKey)
        encryptionKeyPair = KeyPair(private.publicKey, private)
    }

    override fun receiveFromUntrustedHost(bytes: ByteArray): ByteArray {
        val enclaveRequest = EnclaveRequest.deserialize(bytes)

        val response = when (enclaveRequest) {
            is EnclaveRequest.PublicKey -> processRequest(enclaveRequest)
            is EnclaveRequest.PrivateKey -> processRequest(enclaveRequest)
        }

        return response.serialize()
    }

    private fun processRequest(request: EnclaveRequest.PublicKey): EnclaveResponse.PublicKey {
        val signatureElements = generateByteArrayForSigning(request.keySpec.name, request.keySpec.masterKeyType, request.keySpec.policyConstraint, encryptionKeyPair.public.encoded)

        val sign = signer()
        sign.update(signatureElements)

        return EnclaveResponse.PublicKey(encryptionKeyPair.public.encoded, sign.sign(), enclaveInstanceInfo.serialize())
    }

    private fun processRequest(request: EnclaveRequest.PrivateKey): EnclaveResponse.PrivateKey {
        val appEnclaveInstanceInfo = EnclaveInstanceInfo.deserialize(request.appAttestationReport)
        val encodedKeyMail = generateEncodedMail(request, encryptionKeyPair.private.encoded, appEnclaveInstanceInfo)
        return EnclaveResponse.PrivateKey(enclaveInstanceInfo.serialize(), encodedKeyMail)
    }

    private fun generateEncodedMail(privateKeyRequest: EnclaveRequest.PrivateKey, privateKey: ByteArray, appEnclaveInstanceInfo: EnclaveInstanceInfo): ByteArray {
        val envelope = generatePrivateKeyEnvelope(privateKeyRequest.keySpec.name, privateKeyRequest.keySpec.masterKeyType, privateKeyRequest.keySpec.policyConstraint)
        return postOffice(appEnclaveInstanceInfo, UUID.randomUUID().toString()).encryptMail(privateKey, envelope)
    }

    private fun generateByteArrayForSigning(
        name: String,
        masterKeyType: MasterKeyType,
        policyConstraint: String,
        publicKeyEncoded: ByteArray
    ): ByteArray {
        return writeData {
            writeByte(1)
            writeIntLengthPrefixString(name)
            writeByte(masterKeyType.ordinal)
            writeIntLengthPrefixString(policyConstraint)
            writeShortLengthPrefixBytes(publicKeyEncoded)
        }
    }

    private fun generatePrivateKeyEnvelope(name: String, masterKeyType: MasterKeyType, policyConstraint: String): ByteArray {
        return writeData {
            writeByte(1)
            writeIntLengthPrefixString(name)
            writeByte(masterKeyType.ordinal)
            writeIntLengthPrefixString(policyConstraint)
        }
    }
}
