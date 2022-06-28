package com.r3.conclave.common.internal.attestation

import com.r3.conclave.common.EnclaveMode
import com.r3.conclave.common.EnclaveSecurityInfo
import com.r3.conclave.common.OpaqueBytes
import com.r3.conclave.common.internal.*
import com.r3.conclave.common.internal.SgxAttributes.flags
import com.r3.conclave.common.internal.SgxReportBody.attributes
import com.r3.conclave.common.internal.SgxSignedQuote.quote
import com.r3.conclave.common.internal.attestation.AttestationUtils.writeIntLengthPrefixBytes
import com.r3.conclave.utilities.internal.*
import java.io.DataOutputStream
import java.nio.ByteBuffer
import java.security.Signature
import java.security.cert.*
import java.time.Instant
import java.util.*

/**
 * Represents an attestation that was performed by an attestation service on the enclave host. Once created it can be
 * serialised as part of an [EnclaveInstanceInfoImpl].
 *
 * There are only three concrete attestation types representing EPID, DCAP and mock. Only EPID and DCAP can *potentially*
 * create attestations which represent secure enclaves. Mock attestations are always insecure.
 *
 *           |---------|-------|-----|------|
 *           | RELEASE | DEBUG | SIM | MOCK |
 *    |------|---------|-------|-----|------|
 *    | EPID | x       | x     |     |      |
 *    | DCAP | x       | x     |     |      |
 *    | Mock |         |       | x   | x    |
 *    |------|---------|-------|-----|------|
 */
sealed class Attestation {
    abstract val timestamp: Instant

    abstract val reportBody: ByteCursor<SgxReportBody>

    abstract val enclaveMode: EnclaveMode

    abstract val securitySummary: EnclaveSecurityInfo.Summary

    abstract val securityReason: String

    protected abstract fun serialise(): ByteArray

    fun writeTo(out: DataOutputStream) {
        val attestationType = when (this) {
            is EpidAttestation -> 0
            is DcapAttestation -> 1
            is MockAttestation -> 2
        }
        out.write(attestationType)
        out.writeIntLengthPrefixBytes(serialise())
    }

    companion object {
        fun getFromBuffer(buffer: ByteBuffer): Attestation {
            val attestationType = buffer.get()
            val attestationSlice = buffer.getIntLengthPrefixSlice()
            return when (attestationType.toInt()) {
                0 -> EpidAttestation.getFromBuffer(attestationSlice)
                1 -> DcapAttestation.getFromBuffer(attestationSlice)
                2 -> MockAttestation.getFromBuffer(attestationSlice)
                else -> throw IllegalArgumentException("Unknown attestation type $attestationType")
            }
        }
    }
}

/**
 * Represents an attestation that was made on genuine Intel SGX hardware using a real attestation service.
 *
 * @property reportBody Cryptographically verified as coming from an enclave running on actual SGX hardware.
 * The details of the verification is dependent on the attestation protocol.
 *
 * @property enclaveMode Either debug or release and nothing else, depending on the enclave's debug flag. As this flag is
 * part of the verified quote object, it is a true representation of the enclave's environment and cannot be faked.
 *
 * @property securitySummary Always insecure if the [enclaveMode] is debug, otherwise dependent on the verification. If
 * any exception is thrown during the verification then the attestation is automatically insecure.
 */
sealed class HardwareAttestation : Attestation() {
    final override val enclaveMode: EnclaveMode
        get() {
            val flags = reportBody[attributes][flags]
            return if (flags.isSet(SgxEnclaveFlags.DEBUG)) EnclaveMode.DEBUG else EnclaveMode.RELEASE
        }

    final override val securitySummary: EnclaveSecurityInfo.Summary
        get() {
            return if (enclaveMode == EnclaveMode.RELEASE) hardwareSecuritySummary else EnclaveSecurityInfo.Summary.INSECURE
        }

    final override val securityReason: String
        get() {
            return if (enclaveMode == EnclaveMode.RELEASE) {
                hardwareSecurityReason
            } else {
                val underlyingHardware = when (hardwareSecuritySummary) {
                    EnclaveSecurityInfo.Summary.SECURE -> "However the security status of the underlying hardware is secure"
                    EnclaveSecurityInfo.Summary.STALE -> "The security status of the underlying hardware is stale"
                    EnclaveSecurityInfo.Summary.INSECURE -> "The security status of the underlying hardware is also insecure"
                }
                "Enclave is running in debug mode and is thus INSECURE. $underlyingHardware: $hardwareSecurityReason"
            }
        }

    protected abstract val hardwareSecuritySummary: EnclaveSecurityInfo.Summary
    protected abstract val hardwareSecurityReason: String
}

data class EpidAttestation(
    val reportBytes: OpaqueBytes,
    val signature: OpaqueBytes,
    val certPath: CertPath,
) : HardwareAttestation() {
    val report: EpidVerificationReport

    init {
        val certTime = certPath.x509Certs.minOf { it.notAfter }
        val pkixParameters = PKIXParameters(setOf(TrustAnchor(rootCert, null))).apply {
            isRevocationEnabled = false
            date = certTime
        }
        CertPathValidator.getInstance("PKIX").validate(certPath, pkixParameters)

        val reportBytesBytes = reportBytes.bytes

        Signature.getInstance("SHA256withRSA").apply {
            initVerify(certPath.certificates[0])
            update(reportBytesBytes)
            check(verify(signature.bytes)) { "The signature of the EPID attestation report is invalid." }
        }

        report = attestationObjectMapper.readValue(reportBytesBytes, EpidVerificationReport::class.java)

        check(report.version == 4) { "Unknown version of EPID attestation report ${report.version}" }
    }

    override val timestamp: Instant get() = report.timestamp

    override val reportBody: ByteCursor<SgxReportBody> get() = report.isvEnclaveQuoteBody[SgxQuote.reportBody]

    override val hardwareSecuritySummary: EnclaveSecurityInfo.Summary
        get() {
            return when (report.isvEnclaveQuoteStatus) {
                EpidQuoteStatus.OK -> EnclaveSecurityInfo.Summary.SECURE

                EpidQuoteStatus.GROUP_OUT_OF_DATE,
                EpidQuoteStatus.CONFIGURATION_NEEDED,
                EpidQuoteStatus.SW_HARDENING_NEEDED,
                EpidQuoteStatus.CONFIGURATION_AND_SW_HARDENING_NEEDED -> EnclaveSecurityInfo.Summary.STALE

                EpidQuoteStatus.SIGNATURE_INVALID,
                EpidQuoteStatus.GROUP_REVOKED,
                EpidQuoteStatus.SIGNATURE_REVOKED,
                EpidQuoteStatus.KEY_REVOKED,
                EpidQuoteStatus.SIGRL_VERSION_MISMATCH -> EnclaveSecurityInfo.Summary.INSECURE
            }
        }

    override val hardwareSecurityReason: String
        get() {
            return when (report.isvEnclaveQuoteStatus) {
                EpidQuoteStatus.OK -> "A signature of the ISV enclave QUOTE was verified correctly and the TCB " +
                        "level of the SGX platform is up-to-date."
                EpidQuoteStatus.SIGNATURE_INVALID -> "The signature of the ISV enclave QUOTE was invalid. The " +
                        "content of the QUOTE is not trustworthy."
                EpidQuoteStatus.GROUP_REVOKED -> "The EPID group has been revoked (reason=${report.revocationReason}). " +
                        "The content of the QUOTE is not trustworthy."
                EpidQuoteStatus.SIGNATURE_REVOKED -> "The EPID private key used to sign the QUOTE has been revoked " +
                        "by signature. The content of the QUOTE is not trustworthy."
                EpidQuoteStatus.KEY_REVOKED -> "The EPID private key used to sign the QUOTE has been directly " +
                        "revoked (not by signature). The content of the QUOTE is not trustworthy."
                EpidQuoteStatus.SIGRL_VERSION_MISMATCH -> "The Signature Revocation List (SigRL) version in ISV " +
                        "enclave QUOTE does not match the most recent version of the SigRL. Please try again with the " +
                        "most recent version of SigRL from the IAS. Until then the content of the QUOTE is not trustworthy."
                EpidQuoteStatus.GROUP_OUT_OF_DATE -> "The EPID signature of the ISV enclave QUOTE has been verified " +
                        "correctly, but the TCB level of SGX platform is outdated. The platform has not been identified " +
                        "as compromised and thus it is not revoked.${report.advisoryIdsSentence}"
                EpidQuoteStatus.CONFIGURATION_NEEDED -> "The signature of the ISV enclave QUOTE has been verified " +
                        "correctly, but additional configuration of SGX platform may be needed. The platform has not been " +
                        "identified as compromised and thus it is not revoked.${report.advisoryIdsSentence}"
                EpidQuoteStatus.SW_HARDENING_NEEDED -> "The signature of the ISV enclave QUOTE has been verified " +
                        "correctly but due to certain issues affecting the platform, additional software hardening in the " +
                        "attesting SGX enclaves may be needed.${report.advisoryIdsSentence}"
                EpidQuoteStatus.CONFIGURATION_AND_SW_HARDENING_NEEDED -> "The signature of the ISV enclave QUOTE " +
                        "has been verified correctly but additional configuration for the platform and software hardening in " +
                        "the attesting SGX enclaves may be needed. The platform has not been identified as compromised and " +
                        "thus it is not revoked.${report.advisoryIdsSentence}"
            }
        }

    override fun serialise(): ByteArray {
        return writeData {
            writeIntLengthPrefixBytes(reportBytes)
            writeIntLengthPrefixBytes(signature)
            writeIntLengthPrefixBytes(certPath.encoded)
        }
    }

    private val EpidVerificationReport.advisoryIdsSentence: String
        get() {
            return when (advisoryIDs?.size ?: 0) {
                0 -> ""
                else -> " For further details see Advisory IDs ${advisoryIDs!!.joinToString(", ")}."
            }
        }

    companion object {
        private val rootCert = EpidAttestation::class.java.getResourceAsStream("intel-epid-root-cert.pem").use {
            CertificateFactory.getInstance("X.509").generateCertificate(it) as X509Certificate
        }

        fun getFromBuffer(buffer: ByteBuffer): EpidAttestation {
            val reportBytes = OpaqueBytes(buffer.getIntLengthPrefixBytes())
            val signature = OpaqueBytes(buffer.getIntLengthPrefixBytes())
            val certPath =
                CertificateFactory.getInstance("X.509").generateCertPath(buffer.getIntLengthPrefixSlice().inputStream())
            return EpidAttestation(reportBytes, signature, certPath)
        }
    }
}

data class DcapAttestation(
    val signedQuote: ByteCursor<SgxSignedQuote>,
    val collateral: QuoteCollateral
) : HardwareAttestation() {
    override val timestamp: Instant
    private val verificationStatus: VerificationStatus

    init {
        require(signedQuote.isReadOnly)
        val (verificationStatus, latestIssueTime) = QuoteVerifier.verify(signedQuote, collateral)
        timestamp = latestIssueTime
        this.verificationStatus = verificationStatus
    }

    override val reportBody: ByteCursor<SgxReportBody> get() = signedQuote[quote][SgxQuote.reportBody]

    override val hardwareSecuritySummary: EnclaveSecurityInfo.Summary
        get() {
            if (verificationStatus !is TcbStatus) {
                return EnclaveSecurityInfo.Summary.INSECURE
            }

            return when (verificationStatus) {
                TcbStatus.UpToDate -> EnclaveSecurityInfo.Summary.SECURE

                TcbStatus.SWHardeningNeeded,
                TcbStatus.ConfigurationNeeded,
                TcbStatus.ConfigurationAndSWHardeningNeeded,
                TcbStatus.OutOfDate,
                TcbStatus.OutOfDateConfigurationNeeded -> EnclaveSecurityInfo.Summary.STALE

                TcbStatus.Revoked -> EnclaveSecurityInfo.Summary.INSECURE
            }
        }

    override val hardwareSecurityReason: String
        get() {
            if (verificationStatus !is TcbStatus) {
                return "Enclave QUOTE has not been verified correctly and thus is untrustworthy ($verificationStatus)"
            }

            val advisoryIdsSentence = getAdvisoryIdsSentence()
            return when (verificationStatus) {
                TcbStatus.UpToDate -> "A signature of the ISV enclave QUOTE was verified correctly and the TCB level " +
                        "of the SGX platform is up-to-date."
                TcbStatus.SWHardeningNeeded -> "The signature of the ISV enclave QUOTE has been verified " +
                        "correctly but due to certain issues affecting the platform, additional software hardening in the " +
                        "attesting SGX enclaves may be needed.$advisoryIdsSentence"
                TcbStatus.ConfigurationNeeded -> "The signature of the ISV enclave QUOTE has been verified " +
                        "correctly, but additional configuration of SGX platform may be needed. The platform has not been " +
                        "identified as compromised and thus it is not revoked."
                TcbStatus.ConfigurationAndSWHardeningNeeded -> "The signature of the ISV enclave QUOTE " +
                        "has been verified correctly but additional configuration for the platform and software hardening in " +
                        "the attesting SGX enclaves may be needed. The platform has not been identified as compromised and " +
                        "thus it is not revoked.$advisoryIdsSentence"
                TcbStatus.OutOfDate -> "The signature of the ISV enclave QUOTE has been verified " +
                        "correctly, but the TCB level of SGX platform is outdated. The platform has not been identified " +
                        "as compromised and thus it is not revoked.$advisoryIdsSentence"
                TcbStatus.OutOfDateConfigurationNeeded -> "The signature of the ISV enclave QUOTE has been " +
                        "verified correctly, but the TCB level of SGX platform is outdated and additional configuration of " +
                        "it may be needed. The platform has not been identified as compromised and thus it is not revoked.$advisoryIdsSentence"
                TcbStatus.Revoked -> "The TCB level of SGX platform is revoked. The platform is not trustworthy."
            }
        }

    private fun getAdvisoryIdsSentence(): String {
        val advisoryIDs =
            collateral.signedTcbInfo.tcbInfo.tcbLevels.flatMapTo(TreeSet()) { it.advisoryIDs ?: emptySet() }

        return when (advisoryIDs.size) {
            0 -> ""
            else -> " For further details see Advisory IDs ${advisoryIDs.joinToString(", ")}."
        }
    }

    override fun serialise(): ByteArray {
        return writeData {
            write(signedQuote.bytes)
            collateral.serialiseTo(this)
        }
    }

    companion object {
        fun getFromBuffer(buffer: ByteBuffer): DcapAttestation {
            val signedQuote = Cursor.copy(SgxSignedQuote, buffer).asReadOnly()
            val collateral = QuoteCollateral.getFromBuffer(buffer)
            return DcapAttestation(signedQuote, collateral)
        }
    }
}

/**
 * A fake attestation for when the enclave is running in simulation or mock mode.
 *
 * Since neither of these modes involves any actual SGX hardware, the quote object is not verfied against anything. Any
 * value for the quote is permitted. However, the security summary for this attestation is hardcoded to be
 * [EnclaveSecurityInfo.Summary.INSECURE] and so this object cannot be used to fake a secure enclave.
 *
 * The [enclaveMode] is either simulation or mock and nothing else, depending on [isSimulation].
 */
data class MockAttestation(
    override val timestamp: Instant,
    override val reportBody: ByteCursor<SgxReportBody>,
    val isSimulation: Boolean
) : Attestation() {
    init {
        require(reportBody.isReadOnly)
    }

    override val enclaveMode: EnclaveMode get() = if (isSimulation) EnclaveMode.SIMULATION else EnclaveMode.MOCK

    override val securitySummary: EnclaveSecurityInfo.Summary get() = EnclaveSecurityInfo.Summary.INSECURE

    override val securityReason: String get() = "Enclave is running in ${enclaveMode.name.lowercase()} mode."

    override fun serialise(): ByteArray {
        // We use a ByteBuffer (which forces us to pre-allocate the size) to avoid some extra copying when writing the
        // reportBody.
        val buffer = ByteBuffer.allocate(Long.SIZE_BYTES + Int.SIZE_BYTES + reportBody.size + 1)
        buffer.putLong(timestamp.epochSecond)
        buffer.putInt(timestamp.nano)
        buffer.put(reportBody.read())
        buffer.putBoolean(isSimulation)
        return buffer.array()
    }

    companion object {
        fun getFromBuffer(buffer: ByteBuffer): MockAttestation {
            val epochSecond = buffer.getLong()
            val nano = buffer.getInt()
            val reportBody = Cursor.copy(SgxReportBody, buffer).asReadOnly()
            val isSimulation = buffer.getBoolean()
            return MockAttestation(Instant.ofEpochSecond(epochSecond, nano.toLong()), reportBody, isSimulation)
        }
    }
}
