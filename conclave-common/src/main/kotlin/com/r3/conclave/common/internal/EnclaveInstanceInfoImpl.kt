package com.r3.conclave.common.internal

import com.r3.conclave.common.*
import com.r3.conclave.common.EnclaveMode.*
import com.r3.conclave.common.EnclaveSecurityInfo.Summary
import com.r3.conclave.common.internal.SgxAttributes.flags
import com.r3.conclave.common.internal.SgxReportBody.attributes
import com.r3.conclave.common.internal.SgxReportBody.cpuSvn
import com.r3.conclave.common.internal.SgxReportBody.isvProdId
import com.r3.conclave.common.internal.SgxReportBody.isvSvn
import com.r3.conclave.common.internal.SgxReportBody.measurement
import com.r3.conclave.common.internal.SgxReportBody.mrsigner
import com.r3.conclave.common.internal.SgxReportBody.reportData
import com.r3.conclave.common.internal.attestation.AttestationParameters
import com.r3.conclave.common.internal.attestation.AttestationReport
import com.r3.conclave.common.internal.attestation.AttestationResponse
import com.r3.conclave.common.internal.attestation.QuoteStatus.*
import java.security.PublicKey
import java.security.Signature

class EnclaveInstanceInfoImpl(
        override val dataSigningKey: PublicKey,
        val attestationResponse: AttestationResponse,
        val enclaveMode: EnclaveMode
) : EnclaveInstanceInfo {
    // The verification of the parameters are done at construction time. This is especially important when deserialising.
    val attestationReport: AttestationReport
    override val enclaveInfo: EnclaveInfo
    override val securityInfo: EnclaveSecurityInfo

    init {
        val pkixParameters = when (enclaveMode) {
            RELEASE, DEBUG -> AttestationParameters.INTEL
            SIMULATION -> AttestationParameters.MOCK
        }
        attestationReport = attestationResponse.verify(pkixParameters)
        // By successfully verifying with the PKIX parameters we are sure that the enclaveMode is correct it terms of
        // simulation vs non-simulation.

        val reportBody = attestationReport.isvEnclaveQuoteBody[SgxQuote.reportBody]

        val flags = reportBody[attributes][flags].read()
        val isDebug = flags and SgxEnclaveFlags.DEBUG != 0L
        // Now we check the debug flag from the attested quote matches release vs debug enclaveMode. And thus we're sure
        // enclaveMode is correct.
        when (enclaveMode) {
            RELEASE -> require(!isDebug) { "Mismatch between debug flag and enclaveMode" }
            DEBUG, SIMULATION -> require(isDebug) { "Mismatch between debug flag and enclaveMode" }
        }

        val signingKeyHash = SHA512Hash.hash(dataSigningKey.encoded)
        val reportData = SHA512Hash.get(reportBody[reportData].read())
        require(signingKeyHash == reportData) {
            "The report data of the quote does not equal the SHA-512 hash of the data signing key."
        }

        enclaveInfo = EnclaveInfo(
                codeHash = SHA256Hash.get(reportBody[measurement].read()),
                codeSigningKeyHash = SHA256Hash.get(reportBody[mrsigner].read()),
                productID = reportBody[isvProdId].read(),
                revocationLevel = reportBody[isvSvn].read() - 1,
                enclaveMode = enclaveMode
        )

        val (summary, reason) = when (enclaveMode) {
            RELEASE -> getSummaryAndReason(attestationReport)
            DEBUG -> Pair(Summary.INSECURE, "Enclave is running in debug mode.")
            SIMULATION -> Pair(Summary.INSECURE, "Enclave is running in simulation mode.")
        }
        val cpuSVN = OpaqueBytes(reportBody[cpuSvn].readBytes())
        securityInfo = SGXEnclaveSecurityInfo(summary, reason, attestationReport.timestamp, cpuSVN)
    }

    override fun verifier(): Signature {
        val signature = SignatureSchemeEdDSA.createSignature()
        signature.initVerify(dataSigningKey)
        return signature
    }

    // New fields MUST be added to the end, even if they belong in AttestationResponse
    override fun serialize(): ByteArray {
        return writeData {
            write(magic)
            writeLengthPrefixBytes(dataSigningKey.encoded)
            writeLengthPrefixBytes(attestationResponse.reportBytes)
            writeLengthPrefixBytes(attestationResponse.signature)
            writeLengthPrefixBytes(attestationResponse.certPath.encoded)
            write(enclaveMode.ordinal)
        }
    }

    override fun toString() = """
        Remote attestation for enclave ${enclaveInfo.codeHash}:
          - Mode: $enclaveMode
          - Code signing key hash: ${enclaveInfo.codeSigningKeyHash}
          - Product ID: ${enclaveInfo.productID}
          - Revocation level: ${enclaveInfo.revocationLevel}
        
        Assessed security level at ${securityInfo.timestamp} is ${securityInfo.summary}
          - ${securityInfo.reason}
    """.trimIndent()

    companion object {
        private val magic = "EII".toByteArray()

        private fun getSummaryAndReason(report: AttestationReport): Pair<Summary, String> {
            return when (report.isvEnclaveQuoteStatus) {
                OK ->  Pair(Summary.SECURE, "EPID signature of the ISV enclave QUOTE was verified correctly and the TCB " +
                        "level of the SGX platform is up-to-date.")
                SIGNATURE_INVALID -> Pair(Summary.INSECURE, "EPID signature of the ISV enclave QUOTE was invalid. The " +
                        "content of the QUOTE is not trustworthy.")
                GROUP_REVOKED -> Pair(Summary.INSECURE, "The EPID group has been revoked (reason=${report.revocationReason}). " +
                        "The content of the QUOTE is not trustworthy.")
                SIGNATURE_REVOKED -> Pair(Summary.INSECURE, "The EPID private key used to sign the QUOTE has been revoked " +
                        "by signature. The content of the QUOTE is not trustworthy.")
                KEY_REVOKED -> Pair(Summary.INSECURE, "The EPID private key used to sign the QUOTE has been directly " +
                        "revoked (not by signature). The content of the QUOTE is not trustworthy.")
                SIGRL_VERSION_MISMATCH -> Pair(Summary.INSECURE, "The Signature Revocation List (SigRL) version in ISV " +
                        "enclave QUOTE does not match the most recent version of the SigRL. Please try again with the " +
                        "most recent version of SigRL from the IAS. Until then the content of the QUOTE is not trustworthy.")
                GROUP_OUT_OF_DATE -> Pair(Summary.STALE, "The EPID signature of the ISV enclave QUOTE has been verified " +
                        "correctly, but the TCB level of SGX platform is outdated. The platform has not been identified " +
                        "as compromised and thus it is not revoked.${advistoryIdsSentence(report)}")
                CONFIGURATION_NEEDED -> Pair(Summary.STALE, "The EPID signature of the ISV enclave QUOTE has been verified" +
                        "correctly, but additional configuration of SGX platform may be needed. The platform has not been " +
                        "identified as compromised and thus it is not revoked.${advistoryIdsSentence(report)}")
                SW_HARDENING_NEEDED -> Pair(Summary.STALE, "The EPID signature of the ISV enclave QUOTE has been verified " +
                        "correctly but due to certain issues affecting the platform, additional software hardening in the " +
                        "attesting SGX enclaves may be needed.${advistoryIdsSentence(report)}")
                CONFIGURATION_AND_SW_HARDENING_NEEDED -> Pair(Summary.STALE, "The EPID signature of the ISV enclave QUOTE " +
                        "has been verified correctly but additional configuration for the platform and software hardening in " +
                        "the attesting SGX enclaves may be needed. The platform has not been identified as compromised and " +
                        "thus it is not revoked.${advistoryIdsSentence(report)}")
            }
        }

        private fun advistoryIdsSentence(report: AttestationReport): String {
            return when (report.advisoryIDs?.size ?: 0) {
                0 -> ""
                else -> " For further details see Advisory IDs ${report.advisoryIDs!!.joinToString(", ")}."
            }
        }
    }
}
