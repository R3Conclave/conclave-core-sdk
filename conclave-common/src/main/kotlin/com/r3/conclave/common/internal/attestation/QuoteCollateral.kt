package com.r3.conclave.common.internal.attestation

import com.r3.conclave.common.OpaqueBytes
import com.r3.conclave.common.internal.attestation.AttestationUtils.writeIntLengthPrefixBytes
import com.r3.conclave.utilities.internal.getIntLengthPrefixBytes
import com.r3.conclave.utilities.internal.getIntLengthPrefixString
import com.r3.conclave.utilities.internal.writeIntLengthPrefixBytes
import java.io.DataOutputStream
import java.io.InputStreamReader
import java.nio.ByteBuffer
import java.security.cert.CertPath
import java.security.cert.CertificateFactory
import java.security.cert.X509CRL

data class QuoteCollateral(
    val version: Int,
    val rawPckCrlIssuerChain: OpaqueBytes,
    val rawRootCaCrl: OpaqueBytes,
    val rawPckCrl: OpaqueBytes,
    val rawTcbInfoIssuerChain: OpaqueBytes,
    val rawSignedTcbInfo: OpaqueBytes,
    val rawQeIdentityIssuerChain: OpaqueBytes,
    val rawSignedQeIdentity: OpaqueBytes
) {
    val rootCaCrl: X509CRL by lazy { parseCRL(rawRootCaCrl) }

    val pckCrl: X509CRL by lazy { parseCRL(rawPckCrl) }

    val tcbInfoIssuerChain: CertPath by lazy { parseCertPath(rawTcbInfoIssuerChain) }

    val signedTcbInfo: SignedTcbInfo by lazy { parseJson(rawSignedTcbInfo) }

    val qeIdentityIssuerChain: CertPath by lazy { parseCertPath(rawQeIdentityIssuerChain) }

    val signedQeIdentity: SignedEnclaveIdentity by lazy { parseJson(rawSignedQeIdentity) }

    private fun parseCertPath(bytes: OpaqueBytes): CertPath {
        return AttestationUtils.parsePemCertPath(bytes.inputStream())
    }

    private fun parseCRL(bytes: OpaqueBytes): X509CRL {
        return CertificateFactory.getInstance("X.509").generateCRL(bytes.inputStream()) as X509CRL
    }

    private inline fun <reified T> parseJson(bytes: OpaqueBytes): T {
        // This is not correct. Please revise before creating a PR
        return attestationGson.fromJson(InputStreamReader(bytes.inputStream()), T::class.java)
    }

    fun serialiseTo(dos: DataOutputStream) {
        // We need to serialise the version as a length prefixed string to maintain backwards compatibility.
        dos.writeIntLengthPrefixBytes(version.toString().toByteArray())
        dos.writeIntLengthPrefixBytes(rawPckCrlIssuerChain)
        dos.writeIntLengthPrefixBytes(rawRootCaCrl)
        dos.writeIntLengthPrefixBytes(rawPckCrl)
        dos.writeIntLengthPrefixBytes(rawTcbInfoIssuerChain)
        dos.writeIntLengthPrefixBytes(rawSignedTcbInfo)
        dos.writeIntLengthPrefixBytes(rawQeIdentityIssuerChain)
        dos.writeIntLengthPrefixBytes(rawSignedQeIdentity)
    }

    companion object {
        fun getFromBuffer(buffer: ByteBuffer): QuoteCollateral {
            val version = buffer.getIntLengthPrefixString().toInt()
            val rawPckCrlIssuerChain = OpaqueBytes(buffer.getIntLengthPrefixBytes())
            val rawRootCaCrl = OpaqueBytes(buffer.getIntLengthPrefixBytes())
            val rawPckCrl = OpaqueBytes(buffer.getIntLengthPrefixBytes())
            val rawTcbInfoIssuerChain = OpaqueBytes(buffer.getIntLengthPrefixBytes())
            val rawSignedTcbInfo = OpaqueBytes(buffer.getIntLengthPrefixBytes())
            val rawQeIdentityIssuerChain = OpaqueBytes(buffer.getIntLengthPrefixBytes())
            val rawSignedQeIdentity = OpaqueBytes(buffer.getIntLengthPrefixBytes())
            return QuoteCollateral(
                version,
                rawPckCrlIssuerChain,
                rawRootCaCrl,
                rawPckCrl,
                rawTcbInfoIssuerChain,
                rawSignedTcbInfo,
                rawQeIdentityIssuerChain,
                rawSignedQeIdentity
            )
        }
    }
}
