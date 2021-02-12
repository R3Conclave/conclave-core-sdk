package com.r3.conclave.common.internal

import com.r3.conclave.common.*
import com.r3.conclave.common.internal.SgxReportBody.cpuSvn
import com.r3.conclave.common.internal.SgxReportBody.isvProdId
import com.r3.conclave.common.internal.SgxReportBody.isvSvn
import com.r3.conclave.common.internal.SgxReportBody.mrenclave
import com.r3.conclave.common.internal.SgxReportBody.mrsigner
import com.r3.conclave.common.internal.SgxReportBody.reportData
import com.r3.conclave.common.internal.attestation.Attestation
import com.r3.conclave.mail.Curve25519PublicKey
import com.r3.conclave.mail.PostOffice
import com.r3.conclave.utilities.internal.putUnsignedShort
import com.r3.conclave.utilities.internal.toHexString
import com.r3.conclave.utilities.internal.writeData
import com.r3.conclave.utilities.internal.writeIntLengthPrefixBytes
import java.nio.ByteBuffer
import java.security.PrivateKey
import java.security.PublicKey
import java.security.Signature

class EnclaveInstanceInfoImpl(
    override val dataSigningKey: PublicKey,
    override val encryptionKey: Curve25519PublicKey,
    val attestation: Attestation
) : EnclaveInstanceInfo {
    override val enclaveInfo: EnclaveInfo
    override val securityInfo: SGXEnclaveSecurityInfo

    init {
        val reportBody = attestation.reportBody

        val expectedReportDataHash = SHA512Hash.hash(dataSigningKey.encoded + encryptionKey.encoded)
        val reportDataHash = SHA512Hash.get(reportBody[reportData].read())
        require(expectedReportDataHash == reportDataHash) {
            "The report data of the quote does not match the hash in the remote attestation."
        }

        enclaveInfo = EnclaveInfo(
            codeHash = SHA256Hash.get(reportBody[mrenclave].read()),
            codeSigningKeyHash = SHA256Hash.get(reportBody[mrsigner].read()),
            productID = reportBody[isvProdId].read(),
            revocationLevel = reportBody[isvSvn].read() - 1,
            enclaveMode = attestation.enclaveMode
        )

        securityInfo = SGXEnclaveSecurityInfo(
            summary = attestation.securitySummary,
            reason = attestation.securityReason,
            timestamp = attestation.timestamp,
            cpuSVN = OpaqueBytes(reportBody[cpuSvn].bytes)
        )
    }

    override fun verifier(): Signature {
        val signature = SignatureSchemeEdDSA.createSignature()
        signature.initVerify(dataSigningKey)
        return signature
    }

    override fun serialize(): ByteArray {
        return writeData {
            write(magic)
            writeIntLengthPrefixBytes(dataSigningKey.encoded)
            writeIntLengthPrefixBytes(encryptionKey.encoded)
            attestation.writeTo(this)
        }
    }

    val keyDerivation: ByteArray by lazy {
        val buffer = ByteBuffer.allocate(SgxCpuSvn.size + SgxIsvSvn.size)
        securityInfo.cpuSVN.putTo(buffer)
        buffer.putUnsignedShort(enclaveInfo.revocationLevel + 1)
        buffer.array()
    }

    override fun createPostOffice(senderPrivateKey: PrivateKey, topic: String): PostOffice {
        return EIIPostOffice(senderPrivateKey, topic)
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is EnclaveInstanceInfoImpl) return false

        if (dataSigningKey != other.dataSigningKey) return false
        if (encryptionKey != other.encryptionKey) return false
        if (attestation != other.attestation) return false

        return true
    }

    override fun hashCode(): Int {
        var result = dataSigningKey.hashCode()
        result = 31 * result + encryptionKey.hashCode()
        result = 31 * result + attestation.hashCode()
        return result
    }

    override fun toString() = """
        Remote attestation for enclave ${enclaveInfo.codeHash}:
          - Mode: ${enclaveInfo.enclaveMode}
          - Code signing key hash: ${enclaveInfo.codeSigningKeyHash}
          - Public signing key: ${dataSigningKey.encoded.toHexString()}
          - Public encryption key: ${encryptionKey.encoded.toHexString()}
          - Product ID: ${enclaveInfo.productID}
          - Revocation level: ${enclaveInfo.revocationLevel}
        
        Assessed security level at ${securityInfo.timestamp} is ${securityInfo.summary}
          - ${securityInfo.reason}
    """.trimIndent()

    private inner class EIIPostOffice(senderPrivateKey: PrivateKey, topic: String) :
        PostOffice(senderPrivateKey, topic) {
        override val destinationPublicKey: PublicKey get() = this@EnclaveInstanceInfoImpl.encryptionKey
        override val keyDerivation: ByteArray get() = this@EnclaveInstanceInfoImpl.keyDerivation
    }

    companion object {
        private val magic = "EII".toByteArray()
    }
}
