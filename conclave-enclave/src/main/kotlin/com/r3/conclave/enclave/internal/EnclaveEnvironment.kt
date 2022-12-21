package com.r3.conclave.enclave.internal

import com.r3.conclave.common.EnclaveMode
import com.r3.conclave.common.internal.*
import com.r3.conclave.common.internal.attestation.Attestation
import com.r3.conclave.common.internal.kds.EnclaveKdsConfig
import java.nio.ByteBuffer
import java.security.KeyPair
import java.security.PublicKey
import java.util.*

abstract class EnclaveEnvironment(enclaveProperties: Properties, kdsConfig: EnclaveKdsConfig?) {
    companion object {
        // Default enclave properties in the event that the enclave properties resource cannot be loaded.
        // This is required in mock mode because mock enclaves are not always built with the conclave gradle
        // plugin, which is responsible for generating the resource file that usually contains these.
        // These should match the default values generated by the gradle plugin.
        private val defaultProperties: Properties = Properties().apply {
            setProperty("productID", 1.toString())
            setProperty("revocationLevel", 0.toString())
            setProperty("enablePersistentMap", "false")
            setProperty("maxPersistentMapSize", (16 * 1024 * 1024).toString())
            setProperty("inMemoryFileSystemSize", (64 * 1024 * 1024).toString())
            setProperty("persistentFileSystemSize", 0.toString())
            // If this property is not set to true, then the kds is assumed not to be in use, and won't be configured
            // during enclave startup. By default, the KDS is not enabled.
            setProperty("kds.configurationPresent", "false")
            setProperty("kds.persistentKeySpec.configurationPresent", "false")
        }

        // Load enclave properties or optionally get defaults, throw an error if this was unsuccessful
        @JvmStatic
        protected fun loadEnclaveProperties(enclaveClass: Class<*>, allowDefaults: Boolean): Properties {
            val propertyStream = enclaveClass.getResourceAsStream(PluginUtils.ENCLAVE_PROPERTIES)
            val properties = Properties()
            propertyStream?.use {
                properties.load(it)
            } ?: if (allowDefaults) {
                properties.putAll(defaultProperties)
            } else {
                throw IllegalStateException("Failed to load internal enclave properties resource.")
            }
            return properties
        }
    }

    abstract val enclaveMode: EnclaveMode

    abstract val callInterface: CallInterface<HostCallType, EnclaveCallType>

    // Enclave properties from build system
    open val productID: Int = enclaveProperties.getProperty("productID").toInt()
    open val revocationLevel: Int = enclaveProperties.getProperty("revocationLevel").toInt()
    open val enablePersistentMap: Boolean = enclaveProperties.getProperty("enablePersistentMap").toBoolean()
    open val maxPersistentMapSize: Long = enclaveProperties.getProperty("maxPersistentMapSize").toLong()
    open val inMemoryFileSystemSize: Long = enclaveProperties.getProperty("inMemoryFileSystemSize").toLong()
    open val persistentFileSystemSize: Long = enclaveProperties.getProperty("persistentFileSystemSize").toLong()

    // KDS configuration from build system
    open val kdsConfiguration: EnclaveKdsConfig? = kdsConfig ?: EnclaveKdsConfig.loadConfiguration(enclaveProperties)

    /**
     * Create an [SgxReport] of the enclave.
     * @param targetInfo Optional information of the target enclave if the report is to be used as part of local
     * attestation. An example is during quoting when the report is sent to the Quoting Enclave for signing.
     * @param reportData Optional data to be included in the report. If null the data area of the report will be 0.
     */
    abstract fun createReport(
        targetInfo: ByteCursor<SgxTargetInfo>?,
        reportData: ByteCursor<SgxReportData>?
    ): ByteCursor<SgxReport>

    /**
     * Get a [SgxSignedQuote] quote from the host.
     * @param targetInfo Optional information of the target enclave if the report is to be used as part of local
     * attestation. An example is during quoting when the report is sent to the Quoting Enclave for signing.
     * @param reportData Optional data to be included in the report. If null the data area of the report will be 0.
     */
    abstract fun getSignedQuote(
        targetInfo: ByteCursor<SgxTargetInfo>?,
        reportData: ByteCursor<SgxReportData>?
    ): ByteCursor<SgxSignedQuote>

    /**
     * Encrypt and authenticate the given [PlaintextAndEnvelope] using AES-GCM. The key used is unique to the enclave.
     * This method can be used to preserve secret data after the enclave is destroyed. The sealed data blob can be
     * unsealed on future instantiations of the enclave using [unsealData], even if the platform firmware has been
     * updated.
     *
     * @param toBeSealed [PlaintextAndEnvelope] containing the plaintext to be encrypted and an optional public
     * additional data to be included in the authentication.
     * @return the sealed blob output.
     */
    abstract fun sealData(toBeSealed: PlaintextAndEnvelope): ByteArray

    /**
     * Decrypts the given sealed data using AES-GCM so that the enclave data can be restored. This method can be used to
     * restore secret data that was preserved after an earlier instantiation of this enclave.
     * @param sealedBlob the encrypted blob to be decrypted, which is the remaining bytes in the buffer. The buffer's
     * position will be at its limit (i.e. no remaining bytes) after this operation.
     * @return A [PlaintextAndEnvelope] containing the decrypted plaintext and an optional authenticated data if the
     * sealed blob had one.
     * @see sealData
     */
    abstract fun unsealData(sealedBlob: ByteBuffer): PlaintextAndEnvelope

    /**
     * Returns a 128-bit stable pseudo-random secret key based on the given [SgxKeyRequest] object.
     * @param keyRequest Object for selecting the appropriate key and any additional parameters required in the
     * derivation of that key.
     * @return 128-bit secret key.
     */
    abstract fun getSecretKey(keyRequest: ByteCursor<SgxKeyRequest>): ByteArray

    fun getSecretKey(block: (ByteCursor<SgxKeyRequest>) -> Unit): ByteArray {
        val keyRequest = ByteCursor.allocate(SgxKeyRequest)
        block(keyRequest)
        return getSecretKey(keyRequest)
    }

    /**
     * Set up the in-memory and the persistent filesystems.
     * @param inMemoryFsSize Size (bytes) of the in-memory filesystem.
     * @param persistentFsSize Size (bytes) of the persistent encrypted filesystem.
     * @param inMemoryMountPath Mount point of the in-memory filesystem.
     * @param persistentMountPath Mount point of the persistent filesystem.
     * @param encryptionKey Byte array of the encryption key.
     */
    abstract fun setupFileSystems(
        inMemoryFsSize: Long,
        persistentFsSize: Long,
        inMemoryMountPath: String,
        persistentMountPath: String,
        encryptionKey: ByteArray)

    /** Call interface functions */
    /**
     * Send enclave info to the host.
     * TODO: It would be better to return enclave info from the initialise enclave call
     *       but that doesn't work in mock mode at the moment.
     */
    fun setEnclaveInfo(signatureKey: PublicKey, encryptionKeyPair: KeyPair) {
        val encodedSigningKey = signatureKey.encoded                    // 44 bytes
        val encodedEncryptionKey = encryptionKeyPair.public.encoded     // 32 bytes
        val payloadSize = encodedSigningKey.size + encodedEncryptionKey.size
        val buffer = ByteBuffer.allocate(payloadSize).apply {
            put(encodedSigningKey)
            put(encodedEncryptionKey)
        }
        callInterface.executeOutgoingCall(HostCallType.SET_ENCLAVE_INFO, buffer)
    }

    /**
     * Get a signed quote from the host.
     */
    fun getSignedQuote(report: ByteCursor<SgxReport>): ByteCursor<SgxSignedQuote> {
        val quoteBuffer = callInterface.executeOutgoingCallWithReturn(HostCallType.GET_SIGNED_QUOTE, report.buffer)
        return Cursor.slice(SgxSignedQuote, quoteBuffer)
    }

    /**
     * Get quoting enclave info from the host.
     */
    fun getQuotingEnclaveInfo(): ByteCursor<SgxTargetInfo> {
        val infoBuffer = callInterface.executeOutgoingCallWithReturn(HostCallType.GET_QUOTING_ENCLAVE_INFO)
        return Cursor.slice(SgxTargetInfo, infoBuffer)
    }

    /**
     * Request an attestation from the host.
     */
    fun getAttestation(): Attestation {
        val buffer = callInterface.executeOutgoingCallWithReturn(HostCallType.GET_ATTESTATION)
        return Attestation.getFromBuffer(buffer)
    }

    /**
     * Send a response to the host enclave message handler.
     */
    fun sendEnclaveMessageResponse(response: ByteBuffer) {
        callInterface.executeOutgoingCall(HostCallType.CALL_MESSAGE_HANDLER, response)
    }
}

/**
 * Unsealed data holder, used by the sealing conclave API.
 * @param plaintext unsealed text.
 * @param authenticatedData optional authenticated data.
 */
class PlaintextAndEnvelope(val plaintext: ByteArray, val authenticatedData: ByteArray? = null) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is PlaintextAndEnvelope) return false
        if (authenticatedData != null) {
            if (other.authenticatedData == null || !authenticatedData.contentEquals(other.authenticatedData)) {
                return false
            }
        } else if (other.authenticatedData != null) {
            return false
        }
        return plaintext.contentEquals(other.plaintext)
    }

    override fun hashCode(): Int {
        var result = plaintext.contentHashCode()
        result = 31 * result + (authenticatedData?.contentHashCode() ?: 0)
        return result
    }
}
