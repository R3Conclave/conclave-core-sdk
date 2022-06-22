package com.r3.conclave.client

import com.fasterxml.jackson.core.JacksonException
import com.fasterxml.jackson.databind.MapperFeature
import com.fasterxml.jackson.databind.json.JsonMapper
import com.r3.conclave.client.PostOfficeBuilder.Companion.forEnclaveInstance
import com.r3.conclave.client.PostOfficeBuilder.Companion.usingKDSPublicKey
import com.r3.conclave.client.PostOfficeBuilder.Companion.usingKDS
import com.r3.conclave.client.internal.kds.KDSPublicKeyRequest
import com.r3.conclave.client.internal.kds.KDSPublicKeyResponse
import com.r3.conclave.common.EnclaveConstraint
import com.r3.conclave.common.EnclaveInstanceInfo
import com.r3.conclave.common.InvalidEnclaveException
import com.r3.conclave.common.internal.EnclaveInstanceInfoImpl
import com.r3.conclave.common.internal.KdsKeySpecKeyDerivation
import com.r3.conclave.common.internal.MailKeyDerivation
import com.r3.conclave.common.internal.RandomSessionKeyDerivation
import com.r3.conclave.common.kds.KDSKeySpec
import com.r3.conclave.mail.Curve25519PrivateKey
import com.r3.conclave.mail.Curve25519PublicKey
import com.r3.conclave.mail.PostOffice
import com.r3.conclave.utilities.internal.writeData
import com.r3.conclave.utilities.internal.writeIntLengthPrefixString
import com.r3.conclave.utilities.internal.writeShortLengthPrefixBytes
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.PrivateKey
import java.security.PublicKey
import java.security.SignatureException

/**
 * A builder of [PostOffice] objects. There are two types of post offices that can be built:
 * 1. Targeted to a single enclave instance, which are created using [forEnclaveInstance].
 * 2. Using a Key Derivation Service (KDS), which are created using either [usingKDS], [usingKDSResponse] or
 *   [usingKDSPublicKey].
 *
 * The builder can then be modified with [setTopic] to change the topic that will be used with the built post office
 * (default value is "default"), and with [setSenderPrivateKey] to change the sender private key (the default is to use
 * a new random key).
 *
 *  ### Enclave instance
 *
 *  Post offices created for a particular enclave instance will create mail that can only be decrypted by that
 *  enclave instance. The enclave uses a random session key and so another instance, even if it's a clone, will not be
 *  able to intercept and decrypt those mail messages. If the enclave restarts another session key is generated,
 *  along with a slightly different [EnclaveInstanceInfo]. It is recommended [EnclaveClient] be used to automatically
 *  deal with enclave restarts.
 *
 *  ### KDS
 *
 *  An alternative approach to using an enclave session key is to use a stable key from the
 *  [Key Derivation Service (KDS)](https://docs.conclave.net/kds-detail.html). The post office gets the public key
 *  needed to encrypt the mail and the enclave gets the corresponding private key to decrypt it. Only the intended
 *  enclave that matches the key policy ([KDSKeySpec.policyConstraint]) will be able to retrieve the private key from
 *  the KDS. Using the KDS enables architectures which are difficult to implement with just session keys, such as a
 *  running multiple copies of the same enclave in a horizontally scaled application.
 */
class PostOfficeBuilder private constructor(
    private val destinationPublicKey: Curve25519PublicKey,
    private val mailKeyDerivation: MailKeyDerivation
) {
    companion object {
        private val jsonMapper = JsonMapper.builder().enable(MapperFeature.ACCEPT_CASE_INSENSITIVE_ENUMS).build()

        /**
         * Create a new [PostOfficeBuilder] targeted to a single enclave instance. This is done by using the
         * [EnclaveInstanceInfo.encryptionKey] as the destination public key.
         *
         * Post offices created by this builder behave the same as the ones created from
         * [EnclaveInstanceInfo.createPostOffice]. This method is provided here as a convenience.
         *
         * @param enclaveInstanceInfo The [EnclaveInstanceInfo] of the enclave to which mail will be encrypted to.
         *
         * @return A new [PostOfficeBuilder] targeted to the given [EnclaveInstanceInfo].
         *
         * @see [EnclaveInstanceInfo.createPostOffice]
         */
        @JvmStatic
        fun forEnclaveInstance(enclaveInstanceInfo: EnclaveInstanceInfo): PostOfficeBuilder {
            enclaveInstanceInfo as EnclaveInstanceInfoImpl
            return PostOfficeBuilder(enclaveInstanceInfo.encryptionKey, RandomSessionKeyDerivation)
        }

        /**
         * Create a new [PostOfficeBuilder] which will get the public key for encrypting mail from a KDS. A 
         * [KDSKeySpec] is used to specify the exact public key to be used by the post office and the matching
         * private key to be used by the destination enclave.
         *
         * This method will block whilst it makes the public key HTTP REST request on the provided KDS URL. If more
         * control is required on the HTTP connection then use [usingKDSResponse] instead and pass in the KDS response
         * [InputStream].
         *
         * @param kdsHostUrl URL to the KDS without any path components. The "/public" path will be added
         * automatically when making the public key request. Example: https://kds.dev.conclave.cloud
         * @param keySpec The key specification the KDS will use to derive the public key. The enclave will use this
         * same spec to get the corresponding private key to decrypt mail.
         * @param kdsEnclaveConstraint The enclave constraint to identify and validate the KDS enclave which generated 
         * the public key.
         *
         * @return A new [PostOfficeBuilder] configured to use the public key from the KDS.
         *
         * @throws IOException If the public key could not be retrieved from the KDS.
         * @throws SignatureException If the signature validation on the public key response fails. This might
         * indicate a wrong or tampered public key was returned.
         * @throws InvalidEnclaveException If the KDS enclave producing the public key fails the constraints check.
         */
        @Throws(SignatureException::class, IOException::class, InvalidEnclaveException::class)
        @JvmStatic
        fun usingKDS(
            kdsHostUrl: URL,
            keySpec: KDSKeySpec,
            kdsEnclaveConstraint: EnclaveConstraint
        ): PostOfficeBuilder {
            val responseInputStream = requestKdsPublicKey(kdsHostUrl, keySpec)
            return responseInputStream.use {
                usingKDSResponse(it, keySpec, kdsEnclaveConstraint)
            }
        }

        /**
         * Create a new [PostOfficeBuilder] which will get the public key from an [InputStream] representing the
         * response to a KDS public key request. It is the caller's job to first make the HTTP REST for the public key
         * and then pass in the HTTP response body to this method. Details on the REST API can be found
         * [here](https://docs.conclave.net/kds-rest-api.html#endpoint-public).
         *
         * The [KDSKeySpec] provided must be the same one that was used in the public key request, otherwise this
         * method will fail with a [SignatureException]. The destination enclave will use this key spec to get the
         * matching private key from the KDS.
         *
         * Alternatively [usingKDS] can be used, which performs the public key request in addition to processing
         * the response.
         *
         * @param responseStream [InputStream] representing the HTTP response body to a KDS public key REST request.
         * The body is assumed to be encoded in UTF-8. It is the caller's job to close the stream after calling
         * this method.
         * @param keySpec The key specification that was used in the KDS public key request. The enclave will use this
         * same spec to get the corresponding private key to decrypt mail.
         * @param kdsEnclaveConstraint The enclave constraint to identify and validate the KDS enclave which generated
         * the public key.
         *
         * @return A new [PostOfficeBuilder] configured to use the public key from the KDS.
         *
         * @throws IOException If the public key could not be retrieved from the response [InputStream].
         * @throws SignatureException If the signature validation on the public key response fails. This might
         * indicate the wrong [keySpec] was provided or the public key response was tampered with.
         * @throws InvalidEnclaveException If the KDS enclave producing the public key fails the constraints check.
         */
        @Throws(SignatureException::class, IOException::class, InvalidEnclaveException::class)
        @JvmStatic
        fun usingKDSResponse(
            responseStream: InputStream,
            keySpec: KDSKeySpec,
            kdsEnclaveConstraint: EnclaveConstraint
        ): PostOfficeBuilder {
            val body = responseStream.reader().readText()
            val jsonResponse = try {
                jsonMapper.readTree(body)
            } catch (e: JacksonException) {
                throw IOException("Invalid KDS public key response: $body")
            }
            // There are two valid JSON responses when making a public key request:
            // 1. A body containing "publicKey", "signature" and "kdsAttestationReport" which is represented by
            //    KDSPublicKeyResponse
            // 2. An error response which is a JSON body containing the field "reason"
            // There's no "type" field to distinguish between the two, and nor do we have access to the HTTP response
            // code. So we use the "reason" field to determine what type of response this is.
            val errorReason = jsonResponse["reason"]
            if (errorReason != null) {
                // This is an error response.
                throw IOException(errorReason.textValue())
            }
            // Otherwise this must be a public key response.
            val kdsPublicKeyResponse = try {
                jsonMapper.convertValue(jsonResponse, KDSPublicKeyResponse::class.java)
            } catch (e: Exception) {
                null
            }
            if (kdsPublicKeyResponse == null) {
                throw IOException("Invalid KDS public key response: $body")
            }
            if (!checkSignature(keySpec, kdsPublicKeyResponse, kdsEnclaveConstraint)) {
                throw SignatureException("Invalid KDS signature")
            }
            val destinationPublicKey = Curve25519PublicKey(kdsPublicKeyResponse.publicKey)
            return PostOfficeBuilder(destinationPublicKey, KdsKeySpecKeyDerivation(keySpec))
        }

        /**
         * Create a new [PostOfficeBuilder] which uses a public key that was previously retrieved from a KDS. It is
         * the caller's job to make sure the provided [KDSKeySpec] maps to this key, otherwise the destination enclave
         * will not be able to decrypt any mail sent to it. Typically [usingKDS] or [usingKDSResponse] would be used
         * instead.
         *
         * @param destinationPublicKey The public key that was previously retrieved from a KDS.
         * @param keySpec The key specification that was used in the KDS public key request. The enclave will use this
         * same spec to get the corresponding private key to decrypt mail.
         *
         * @return A new [PostOfficeBuilder] configured to use the public key from the KDS.
         */
        @JvmStatic
        fun usingKDSPublicKey(destinationPublicKey: PublicKey, keySpec: KDSKeySpec): PostOfficeBuilder {
            // This is a runtime check so we can switch to JDK11+ types later without breaking our own API.
            require(destinationPublicKey is Curve25519PublicKey) {
                "At this time only Conclave originated Curve25519 public keys may be used."
            }
            return PostOfficeBuilder(destinationPublicKey, KdsKeySpecKeyDerivation(keySpec))
        }

        private fun requestKdsPublicKey(kdsHostUrl: URL, keySpec: KDSKeySpec): InputStream {
            val publicKeyRequest = KDSPublicKeyRequest(keySpec.name, keySpec.masterKeyType, keySpec.policyConstraint)
            val publicKeyUri = kdsHostUrl.toURI().resolve("/public")
            val con: HttpURLConnection = publicKeyUri.toURL().openConnection() as HttpURLConnection
            con.requestMethod = "POST"
            con.setRequestProperty("API-VERSION", "1")
            con.setRequestProperty("Content-Type", "application/json; utf-8")
            con.doOutput = true
            con.outputStream.use {
                jsonMapper.writeValue(it, publicKeyRequest)
            }
            return con.inputStream
        }

        private fun checkSignature(
            keySpec: KDSKeySpec,
            kdsPublicResponse: KDSPublicKeyResponse,
            kdsEnclaveConstraint: EnclaveConstraint
        ): Boolean {
            val kdsEii = EnclaveInstanceInfo.deserialize(kdsPublicResponse.kdsAttestationReport)
            kdsEnclaveConstraint.check(kdsEii)
            val verificationData = writeData {
                writeByte(1)
                writeIntLengthPrefixString(keySpec.name)
                writeByte(keySpec.masterKeyType.id)
                writeIntLengthPrefixString(keySpec.policyConstraint)
                writeShortLengthPrefixBytes(kdsPublicResponse.publicKey)
            }
            return with(kdsEii.verifier()) {
                update(verificationData)
                verify(kdsPublicResponse.signature)
            }
        }
    }

    private var topic: String = "default"
    private var senderPrivateKey: Curve25519PrivateKey? = null

    /**
     * Set the topic the post office will use. Defaults to "default".
     *
     * @return This [PostOfficeBuilder] instance.
     *
     * @see [PostOffice.topic]
     */
    fun setTopic(topic: String): PostOfficeBuilder {
        this.topic = topic
        return this
    }

    /**
     * Set the sender private key that will be used by the built [PostOffice] when it creates encrypted mail. If this
     * isn't specified then a new random sender key is created with each post office instance.
     *
     * @param senderPrivateKey The sender private key to be used with all built post offices.
     *
     * @return This [PostOfficeBuilder] instance.
     *
     * @see [PostOffice.senderPrivateKey]
     */
    fun setSenderPrivateKey(senderPrivateKey: PrivateKey): PostOfficeBuilder {
        // This is a runtime check so we can switch to JDK11+ types later without breaking our own API.
        require(senderPrivateKey is Curve25519PrivateKey) {
            "At this time only Conclave originated Curve25519 private keys may be used."
        }
        this.senderPrivateKey = senderPrivateKey
        return this
    }

    /**
     * Builds a new [PostOffice] object.
     */
    fun build(): PostOffice {
        val senderPrivateKey = this.senderPrivateKey ?: Curve25519PrivateKey.random()
        return PostOfficeImpl(destinationPublicKey, mailKeyDerivation, senderPrivateKey, topic)
    }


    private class PostOfficeImpl(
        override val destinationPublicKey: Curve25519PublicKey,
        mailKeyDerivation: MailKeyDerivation,
        senderPrivateKey: Curve25519PrivateKey,
        topic: String
    ) : PostOffice(senderPrivateKey, topic) {
        override val keyDerivation: ByteArray = mailKeyDerivation.serialise()
    }
}
