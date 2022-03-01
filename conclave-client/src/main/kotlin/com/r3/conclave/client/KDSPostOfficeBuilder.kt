package com.r3.conclave.client

import com.r3.conclave.client.internal.KDSPostOffice
import com.r3.conclave.common.EnclaveInstanceInfo
import com.r3.conclave.common.internal.kds.KDSErrorResponse
import com.r3.conclave.client.internal.kds.KDSPublicKeyRequest
import com.r3.conclave.client.internal.kds.KDSPublicKeyResponse
import com.r3.conclave.common.internal.kds.KDSUtils.getJsonMapper
import com.r3.conclave.common.kds.KDSKeySpec
import com.r3.conclave.mail.Curve25519PrivateKey
import com.r3.conclave.mail.Curve25519PublicKey
import com.r3.conclave.mail.PostOffice
import com.r3.conclave.utilities.internal.writeData
import com.r3.conclave.utilities.internal.writeIntLengthPrefixBytes
import com.r3.conclave.utilities.internal.writeIntLengthPrefixString
import net.i2p.crypto.eddsa.EdDSAEngine
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.PrivateKey
import java.security.PublicKey
import java.security.SignatureException

/**
 * Represents a builder to generate a specific PostOffice that uses a custom public key to encrypt messages
 * for the enclave.
 * The users can choose among three different ways of retrieving the custom public key
 * from the Key Derivation Service (KDS).
 * The users can also optionally define a custom topic for the post office communications with the enclave and
 * a custom sender private key for the authentication of the mails to the enclave.
 */
class KDSPostOfficeBuilder private constructor(
    private val destinationPublicKey: PublicKey,
    private val keySpec: KDSKeySpec
) {
    private var topic: String = "default"
    private var senderPrivateKey: PrivateKey? = null

    companion object {

        /**
         * Returns a KDSPostOfficeBuilder instance to build a specific PostOffice that uses a custom public key
         * to encrypt messages for the enclave.
         * If such public key is not derived from the key specification that identifies the enclave,
         * the enclave would not be able to decrypt mail messages.
         * @param publicKey a Curve25519 public key corresponding to a private key that was
         * created using key material derived from the given key specification (keySpec).
         * It is assumed that this public key has been previously obtained from the KDS.
         * The public key will be used to encrypt messages for the enclave.
         * @param keySpec a set of parameters that provides the configuration for a public/private key pair.
         * The enclave will need to obtain the private key associated with this key specification to
         * decrypt mail messages created by the post office.
         * @return a KDSPostOfficeBuilder.
         */
        @JvmStatic
        fun using(destinationPublicKey: PublicKey, keySpec: KDSKeySpec): KDSPostOfficeBuilder {
            return KDSPostOfficeBuilder(destinationPublicKey, keySpec)
        }

        /**
         * Returns a KDSPostOfficeBuilder instance to build a specific PostOffice that uses a custom public key
         * to encrypt messages for the enclave.
         * The public key is requested to the KDS identified by the URL parameter.
         * A public key request to such URL will automatically use the "/public" endpoint.
         * If such public key is not derived from the key specification that identifies the enclave,
         * the enclave would not be able to decrypt mail messages.
         * @param kdsURL URL of the KDS for the retrieval of the public key to be used to send mail to the enclave.
         * Note that the users only need to set the host name (and optionally the port): the end point "/public" will be added automatically.
         * For example a valid kdsURL is: https://kds.dev.conclave.cloud
         * The public key retrieved from the KDS will be used to encrypt messages for the enclave.
         * The key specification used for the derivation of the public key will also need to be used
         * by the enclave in the request of the private key which is needed to decrypt mail messages.
         * @param keySpec a set of parameters that provides the configuration for a public/private key pair.
         * The enclave will need to obtain the private key associated with this key specification to
         * decrypt mail messages created by the post office.
         * @return a KDSPostOfficeBuilder.
         * @throws IOException If there is an I/O error when communicating with the KDS
         * @throws SignatureException If the signature validation when requesting the public key fails. This
         * might indicate that a wrong or tampered public key is returned.
         */
        @Throws(SignatureException::class, IOException::class)
        @JvmStatic
        fun fromUrl(kdsUrl: URL, keySpec: KDSKeySpec): KDSPostOfficeBuilder {
            val destinationPublicKey = retrievePublicKeyFromUrl(kdsUrl, keySpec)
            return KDSPostOfficeBuilder(destinationPublicKey, keySpec)
        }

        /**
         * Returns a KDSPostOfficeBuilder instance to build a specific PostOffice that uses a custom public key
         * to encrypt messages for the enclave.
         * If such public key is not derived from the key specification that identifies the enclave,
         * the enclave would not be able to decrypt mail messages.
         * @param inputStream InputStream representing a connection with the KDS. This gives the users the possibility
         * of using their own networking libraries to connect to the KDS.
         * Note that the users will need to implement the request of the public key by themselves. The input stream
         * represents the content of the response from the KDS, containing the public key.
         * The public key retrieved from the KDS will be used to encrypt messages for the enclave.
         * The key specification used for the derivation of the public key will also need to be used
         * by the enclave in the request of the private key which is needed to decrypt mail messages.
         * It is responsibility of the users to close the InputStream.
         * @param keySpec a set of parameters that provides the configuration for a public/private key pair.
         * The enclave will need to obtain the private key associated with this key specification to
         * decrypt mail messages created by the post office.
         * @return a KDSPostOfficeBuilder.
         * @throws IOException If there is an I/O error when communicating with the KDS
         * @throws SignatureException If the signature validation when requesting the public key fails. This
         * might indicate that a wrong or tampered public key is returned.
         */
        @Throws(SignatureException::class, IOException::class)
        @JvmStatic
        fun fromInputStream(inputStream: InputStream, keySpec: KDSKeySpec): KDSPostOfficeBuilder {
            val destinationPublicKey = retrievePublicKeyFromStream(inputStream, keySpec)
            return KDSPostOfficeBuilder(destinationPublicKey, keySpec)
        }

        private fun requestKdsPublicKeyWithURL(kdsUrl: URL, keySpec: KDSKeySpec): InputStream {
            val mapper = getJsonMapper()
            val publicKeyRequest = KDSPublicKeyRequest(keySpec.name, keySpec.masterKeyType, keySpec.policyConstraint)
            val uri = kdsUrl.toURI().resolve("/public")
            val publicUrl = uri.toURL()
            val con: HttpURLConnection = publicUrl.openConnection() as HttpURLConnection
            con.requestMethod = "POST"
            con.setRequestProperty("Content-Type", "application/json; utf-8")
            con.doOutput = true

            con.outputStream.use {
                mapper.writeValue(it, publicKeyRequest)
            }

            if (con.responseCode != HttpURLConnection.HTTP_OK) {
                val errorString = con.errorStream.use { it.reader().readText() }
                val kdsErrorResponse = try {
                    mapper.readValue(errorString, KDSErrorResponse::class.java)
                } catch (exception: Exception) {
                    // It is likely that the error response is not a KDSErrorResponse if an exception is raised
                    // The best thing to do in those cases is to return the response code
                    throw IOException(
                        "HTTP response code: ${con.responseCode}, HTTP response message: $errorString",
                        exception
                    )
                }
                throw IOException(kdsErrorResponse.reason)
            }
            return con.inputStream
        }

        private fun checkSignature(keySpec: KDSKeySpec, kdsPublicResponse: KDSPublicKeyResponse): Boolean {
            val verificationData = writeData {
                writeIntLengthPrefixString(keySpec.name)
                writeIntLengthPrefixString(keySpec.masterKeyType.name.lowercase())
                writeIntLengthPrefixString(keySpec.policyConstraint)
                writeIntLengthPrefixBytes(kdsPublicResponse.publicKey)
            }
            val eii = EnclaveInstanceInfo.deserialize(kdsPublicResponse.kdsAttestationReport)

            val sig = EdDSAEngine()
            sig.initVerify(eii.dataSigningKey)
            sig.update(verificationData)
            return sig.verify(kdsPublicResponse.signature)
        }


        private fun retrievePublicKeyFromUrl(kdsUrl: URL, keySpec: KDSKeySpec): PublicKey {
            val responseInputStream = requestKdsPublicKeyWithURL(kdsUrl, keySpec)

            val publicKey = responseInputStream.use {
                retrievePublicKeyFromStream(responseInputStream, keySpec)
            }
            return publicKey
        }

        private fun retrievePublicKeyFromStream(inputStream: InputStream, keySpec: KDSKeySpec): PublicKey {
            val mapper = getJsonMapper()
            val kdsPublicKeyResponse = try {
                mapper.readValue(inputStream, KDSPublicKeyResponse::class.java)
            } catch (e: Exception) {
                throw IOException("Invalid KDS response", e)
            }
            val publicKey = Curve25519PublicKey(kdsPublicKeyResponse.publicKey)

            if (!checkSignature(keySpec, kdsPublicKeyResponse)) {
                throw SignatureException("Invalid signature")
            }
            return publicKey
        }
    }

    /**
     * Set a topic for the mail messages of the Post Office that is going to be built.
     * @param topic an optional topic that the users can define when sending/receiving mails
     * with the PostOffice that will get built.
     * The default value is "default".
     * @return a KDSPostOfficeBuilder.
     */
    fun setTopic(topic: String): KDSPostOfficeBuilder {
        this.topic = topic
        return this
    }

    /**
     * Set a custom sender private key for the mail messages of the Post Office that is going to be built.
     * This will be used to authenticate mail messages from this post office.
     * @param senderPrivateKey a Curve25519 private key that the users can provide to authenticate mail messages
     * of this post office.
     * If not specified a random private key will be used.
     * @return a KDSPostOfficeBuilder.
     */
    fun setSenderPrivateKey(senderPrivateKey: PrivateKey): KDSPostOfficeBuilder {
        this.senderPrivateKey = senderPrivateKey
        return this
    }

    /**
     * Builds a new PostOffice object that uses a custom public key to encrypt messages for the enclave.
     * If such public key is not derived from the key specification that identifies the enclave,
     * the enclave will not be able to decrypt mail messages.
     * @return a new PostOffice object.
     */
    fun build(): PostOffice {
        val privateKey = senderPrivateKey ?: Curve25519PrivateKey.random()
        return KDSPostOffice(destinationPublicKey, privateKey, topic, keySpec)
    }

}
