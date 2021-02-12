package com.r3.conclave.common.internal.attestation

import com.r3.conclave.common.OpaqueBytes
import com.r3.conclave.common.internal.*
import com.r3.conclave.common.internal.SgxEcdsa256BitQuoteAuthData.ecdsa256BitSignature
import com.r3.conclave.common.internal.SgxEcdsa256BitQuoteAuthData.ecdsaAttestationKey
import com.r3.conclave.common.internal.SgxEcdsa256BitQuoteAuthData.qeAuthData
import com.r3.conclave.common.internal.SgxEcdsa256BitQuoteAuthData.qeCertData
import com.r3.conclave.common.internal.SgxEcdsa256BitQuoteAuthData.qeReport
import com.r3.conclave.common.internal.SgxEcdsa256BitQuoteAuthData.qeReportSignature
import com.r3.conclave.common.internal.SgxQuote.version
import com.r3.conclave.common.internal.SgxReportBody.isvProdId
import com.r3.conclave.common.internal.SgxReportBody.isvSvn
import com.r3.conclave.common.internal.SgxReportBody.miscSelect
import com.r3.conclave.common.internal.SgxReportBody.mrsigner
import com.r3.conclave.common.internal.SgxReportBody.reportData
import com.r3.conclave.common.internal.SgxSignedQuote.quote
import com.r3.conclave.common.internal.attestation.AttestationUtils.SGX_FMSPC_OID
import com.r3.conclave.common.internal.attestation.AttestationUtils.SGX_PCEID_OID
import com.r3.conclave.common.internal.attestation.AttestationUtils.SGX_PCESVN_OID
import com.r3.conclave.common.internal.attestation.AttestationUtils.SGX_TCB_OID
import com.r3.conclave.common.internal.attestation.AttestationUtils.parseRawEcdsaToDerEncoding
import com.r3.conclave.common.internal.attestation.AttestationUtils.sgxExtension
import com.r3.conclave.common.internal.attestation.QuoteVerifier.EnclaveReportStatus.*
import com.r3.conclave.common.internal.attestation.QuoteVerifier.ErrorStatus.*
import com.r3.conclave.utilities.internal.digest
import com.r3.conclave.utilities.internal.getUnsignedInt
import com.r3.conclave.utilities.internal.x509Certs
import java.security.GeneralSecurityException
import java.security.Signature
import java.security.cert.*
import java.time.Instant
import javax.security.auth.x500.X500Principal

object QuoteVerifier {

    enum class ErrorStatus : VerificationStatus {
        INVALID_PCK_CERT,
        PCK_REVOKED,
        INVALID_PCK_CRL,
        TCB_INFO_MISMATCH,
        UNSUPPORTED_CERT_FORMAT,
        SGX_ROOT_CA_MISSING,
        SGX_INTERMEDIATE_CA_MISSING,
        SGX_PCK_MISSING,
        SGX_CRL_UNKNOWN_ISSUER,
        SGX_CRL_INVALID_EXTENSIONS,
        SGX_CRL_INVALID_SIGNATURE,
        SGX_INTERMEDIATE_CA_REVOKED,
        SGX_PCK_REVOKED,
        SGX_TCB_SIGNING_CERT_MISSING,
        SGX_TCB_SIGNING_CERT_REVOKED,
        TCB_INFO_INVALID_SIGNATURE,
        SGX_ENCLAVE_IDENTITY_INVALID_SIGNATURE,
        INVALID_QE_REPORT_DATA,
        UNSUPPORTED_QUOTE_FORMAT,
        QE_IDENTITY_MISMATCH,
        INVALID_QE_REPORT_SIGNATURE,
        INVALID_QUOTE_SIGNATURE
    }

    private const val QUOTE_VERSION = 3

    private const val INTEL_SUBJECT_PREFIX = "C=US, ST=CA, L=Santa Clara, O=Intel Corporation, "
    private val SGX_ROOT_CA_DN = X500Principal(INTEL_SUBJECT_PREFIX + "CN=Intel SGX Root CA")
    private val SGX_INTERMEDIATE_DN_PLATFORM = X500Principal(INTEL_SUBJECT_PREFIX + "CN=Intel SGX PCK Platform CA")
    private val SGX_INTERMEDIATE_DN_PROCESSOR = X500Principal(INTEL_SUBJECT_PREFIX + "CN=Intel SGX PCK Processor CA")
    private val SGX_PCK_DN = X500Principal(INTEL_SUBJECT_PREFIX + "CN=Intel SGX PCK Certificate")
    private val SGX_TCB_SIGNING_DN = X500Principal(INTEL_SUBJECT_PREFIX + "CN=Intel SGX TCB Signing")

    /*
     * These certificates are hardcoded at Intel's DCAP source code:
     * linux-sgx/external/dcap_source/QuoteVerification/QvE/Include/sgx_qve_def.h
     * #define TRUSTED_ROOT_CA_CERT "-----BEGIN CERTIFICATE-----..."
     * #define TRUSTED_ROOT_CA_CERT_V3 "-----BEGIN CERTIFICATE-----..."
     *
     * we have to pick one based on the quote collateral version
     * linux-sgx/external/dcap_source/QuoteVerification/QvE/Enclave/qve.cpp
     * if (p_quote_collateral->version == QVE_COLLATERAL_VERSION1)
     *     quote_trusted_root_ca_cert = TRUSTED_ROOT_CA_CERT;
     * else if (p_quote_collateral->version == QVE_COLLATERAL_VERSION3)
     *     quote_trusted_root_ca_cert = TRUSTED_ROOT_CA_CERT_V3;
     */
    private val trustedRootCertV1: X509Certificate = javaClass.getResourceAsStream("intel-dcap-root-cert-v1.pem").use {
        CertificateFactory.getInstance("X.509").generateCertificate(it) as X509Certificate
    }

    private val trustedRootCertV3: X509Certificate = javaClass.getResourceAsStream("intel-dcap-root-cert-v3.pem").use {
        CertificateFactory.getInstance("X.509").generateCertificate(it) as X509Certificate
    }

    private fun trustedRootCert(version: String): X509Certificate {
        return when (version) {
            "1" -> trustedRootCertV1
            "3" -> trustedRootCertV3
            else -> throw IllegalArgumentException("Invalid collateral version $version")
        }
    }

    // QuoteVerification/QvE/Enclave/qve.cpp:sgx_qve_verify_quote
    fun verify(
        signedQuote: ByteCursor<SgxSignedQuote>,
        collateral: QuoteCollateral
    ): Pair<VerificationStatus, Instant> {
        val authData = signedQuote.toEcdsaP256AuthData()
        val pckCertPath = authData[qeCertData].toPckCertPath()

        val verificationStatus = try {
            verifyPckCertificate(pckCertPath, collateral)
            verifyTcbInfo(collateral)
            verifyQeIdentity(collateral)
            verifyQuote(
                signedQuote[quote],
                authData,
                pckCertPath.x509Certs[0],
                collateral.pckCrl,
                collateral.signedTcbInfo.tcbInfo,
                collateral.signedQeIdentity.enclaveIdentity
            )
        } catch (e: VerificationException) {
            e.status
        }

        val latestIssueTime = getLatestIssueTime(pckCertPath, collateral)

        return Pair(verificationStatus, latestIssueTime)
    }

    private fun getLatestIssueTime(pckCertPath: CertPath, collateral: QuoteCollateral): Instant {
        var latestIssueTime = trustedRootCert(collateral.version).notBefore.toInstant()
        latestIssueTime = pckCertPath.x509Certs.maxOf(latestIssueTime) { it.notBefore.toInstant() }
        latestIssueTime = maxOf(collateral.rootCaCrl.thisUpdate.toInstant(), latestIssueTime)
        latestIssueTime = maxOf(collateral.pckCrl.thisUpdate.toInstant(), latestIssueTime)
        latestIssueTime = collateral.tcbInfoIssuerChain.x509Certs.maxOf(latestIssueTime) { it.notBefore.toInstant() }
        latestIssueTime = maxOf(collateral.signedTcbInfo.tcbInfo.issueDate, latestIssueTime)
        latestIssueTime = collateral.signedTcbInfo.tcbInfo.tcbLevels.maxOf(latestIssueTime) { it.tcbDate }
        latestIssueTime = collateral.qeIdentityIssuerChain.x509Certs.maxOf(latestIssueTime) { it.notBefore.toInstant() }
        latestIssueTime = maxOf(collateral.signedQeIdentity.enclaveIdentity.issueDate, latestIssueTime)
        latestIssueTime = collateral.signedQeIdentity.enclaveIdentity.tcbLevels.maxOf(latestIssueTime) { it.tcbDate }
        return latestIssueTime
    }

    private inline fun <T> List<T>.maxOf(latestIssueTime: Instant, selector: (T) -> Instant): Instant {
        val maxTime = maxOfOrNull(selector) ?: return latestIssueTime
        return maxOf(maxTime, latestIssueTime)
    }

    /// QuoteVerification/QVL/Src/AttestationLibrary/src/Verifiers/QuoteVerifier.cpp:176
    private fun verifyQuote(
        quote: ByteCursor<SgxQuote>,
        authData: ByteCursor<SgxEcdsa256BitQuoteAuthData>,
        pckCert: X509Certificate,
        pckCrl: X509CRL,
        tcbInfo: TcbInfo,
        qeIdentity: EnclaveIdentity
    ): TcbStatus {
        verify(quote[version].read() == QUOTE_VERSION, UNSUPPORTED_QUOTE_FORMAT)

        verify(SGX_PCK_DN == pckCert.subjectX500Principal, INVALID_PCK_CERT)
        verify(
            listOf(SGX_INTERMEDIATE_DN_PLATFORM, SGX_INTERMEDIATE_DN_PROCESSOR).contains(pckCrl.issuerX500Principal),
            INVALID_PCK_CRL
        )

        verify(pckCrl.issuerX500Principal == pckCert.issuerX500Principal, INVALID_PCK_CRL)
        verify(!pckCrl.isRevoked(pckCert), PCK_REVOKED)

        val pckExtension = pckCert.sgxExtension

        verify(pckExtension.getBytes(SGX_FMSPC_OID) == tcbInfo.fmspc.buffer(), TCB_INFO_MISMATCH)
        verify(pckExtension.getBytes(SGX_PCEID_OID) == tcbInfo.pceId.buffer(), TCB_INFO_MISMATCH)
        verifyQeReportSignature(authData, pckCert)
        verifyAttestationKeyAndQeReportDataHash(authData)

        val qeIdentityStatus = verifyEnclaveReport(authData[qeReport], qeIdentity)
        when (qeIdentityStatus) {
            MISCSELECT_MISMATCH,
            ATTRIBUTES_MISMATCH,
            MRSIGNER_MISMATCH,
            ISVPRODID_MISMATCH -> throw VerificationException(QE_IDENTITY_MISMATCH)

            else -> {
            }
        }

        verifyIsvReportSignature(authData, quote)

        val tcbLevelStatus = checkTcbLevel(pckExtension, tcbInfo)
        return convergeTcbStatus(tcbLevelStatus, qeIdentityStatus)
    }

    private fun verifyIsvReportSignature(
        authData: ByteCursor<SgxEcdsa256BitQuoteAuthData>,
        quote: ByteCursor<SgxQuote>
    ) {
        Signature.getInstance("SHA256withECDSA").apply {
            initVerify(authData[ecdsaAttestationKey].toPublicKey())
            update(quote.buffer)
            verify(verify(authData[ecdsa256BitSignature].toDerEncoding()), INVALID_QUOTE_SIGNATURE)
        }
    }

    private fun verifyQeReportSignature(authData: ByteCursor<SgxEcdsa256BitQuoteAuthData>, pckCert: X509Certificate) {
        Signature.getInstance("SHA256withECDSA").apply {
            initVerify(pckCert)
            update(authData[qeReport].buffer)
            verify(verify(authData[qeReportSignature].toDerEncoding()), INVALID_QE_REPORT_SIGNATURE)
        }
    }

    /// QuoteVerification/QVL/Src/AttestationLibrary/src/QuoteVerification.cpp:77 - parsing inputs
    /// QuoteVerification/QVL/Src/AttestationLibrary/src/Verifiers/PckCertVerifier.cpp:51 - verification
    private fun verifyPckCertificate(pckCertPath: CertPath, collateral: QuoteCollateral) {
        validateCertPath(pckCertPath, collateral.version)

        // Intel assumes a pck chain of 3 certs
        verify(pckCertPath.certificates.size == 3, UNSUPPORTED_CERT_FORMAT)

        // expected Intel's certs
        val (pckCert, intermediateCert, rootCert) = pckCertPath.x509Certs
        verify(SGX_ROOT_CA_DN == rootCert.subjectX500Principal, SGX_ROOT_CA_MISSING)
        verify(
            listOf(
                SGX_INTERMEDIATE_DN_PLATFORM,
                SGX_INTERMEDIATE_DN_PROCESSOR
            ).contains(intermediateCert.subjectX500Principal), SGX_INTERMEDIATE_CA_MISSING
        )
        verify(SGX_PCK_DN == pckCert.subjectX500Principal, SGX_PCK_MISSING)

        // CRLs are coming from collateral
        verifyAgainstCrl(rootCert, collateral.rootCaCrl)
        verifyAgainstCrl(intermediateCert, collateral.pckCrl)

        verify(!collateral.rootCaCrl.isRevoked(intermediateCert), SGX_INTERMEDIATE_CA_REVOKED)
        verify(!collateral.pckCrl.isRevoked(pckCert), SGX_PCK_REVOKED)
    }

    /// QuoteVerification/QVL/Src/AttestationLibrary/src/QuoteVerification.cpp:64 - parsing inputs
    /// QuoteVerification/QVL/Src/AttestationLibrary/src/Verifiers/TCBInfoVerifier.cpp:59
    /// QuoteVerification/QVL/Src/AttestationLibrary/src/Verifiers/TCBSigningChain.cpp:51
    private fun verifyTcbInfo(collateral: QuoteCollateral) {
        verifyTcbChain(collateral.tcbInfoIssuerChain, collateral.rootCaCrl, collateral.version)
        verifyJsonSignature(
            collateral.rawSignedTcbInfo,
            """{"tcbInfo":""",
            collateral.signedTcbInfo.signature,
            collateral.tcbInfoIssuerChain.x509Certs[0],
            TCB_INFO_INVALID_SIGNATURE
        )
    }

    /// QuoteVerification/QVL/Src/AttestationLibrary/src/QuoteVerification.cpp:232
    /// QuoteVerification/QVL/Src/AttestationLibrary/src/Verifiers/EnclaveIdentityVerifier.cpp:60
    private fun verifyQeIdentity(collateral: QuoteCollateral) {
        // yes, verifyTcbChain is used to verify qeIdentityIssuerChain
        verifyTcbChain(collateral.qeIdentityIssuerChain, collateral.rootCaCrl, collateral.version)
        verifyJsonSignature(
            collateral.rawSignedQeIdentity,
            """{"enclaveIdentity":""",
            collateral.signedQeIdentity.signature,
            collateral.qeIdentityIssuerChain.x509Certs[0],
            SGX_ENCLAVE_IDENTITY_INVALID_SIGNATURE
        )
    }

    private fun verifyJsonSignature(
        rawJson: String,
        prefix: String,
        rawSignature: OpaqueBytes,
        cert: X509Certificate,
        errorStatus: ErrorStatus
    ) {
        // The documentation at https://api.portal.trustedservices.intel.com/documentation would have you believe that
        // simply removing the whitespace from the body is all that's needed to verify with the signature. However
        // JSON objects are *unordered* key/value pairs, and the encoding for any hex fields for this API accepts both
        // upper and lower case chars. So for these reasons we play it safe and verify over the body as it appears in the
        // raw JSON string.
        val body = rawJson
            .take(rawJson.lastIndexOf("""},"signature":"""") + 1)
            .drop(prefix.length)
        val verifier = Signature.getInstance("SHA256withECDSA")
        verifier.initVerify(cert)
        verifier.update(body.toByteArray())
        val signature = parseRawEcdsaToDerEncoding(rawSignature.buffer())
        verify(verifier.verify(signature), errorStatus)
    }

    /// QuoteVerification/QVL/Src/AttestationLibrary/src/Verifiers/TCBSigningChain.cpp:56
    private fun verifyTcbChain(tcbSignChain: CertPath, rootCaCrl: X509CRL, collateralVersion: String) {
        validateCertPath(tcbSignChain, collateralVersion)

        // Intel assumes a tcb chain of 2 certs
        verify(tcbSignChain.certificates.size == 2, UNSUPPORTED_CERT_FORMAT)

        val (tcbSigningCert, rootCert) = tcbSignChain.x509Certs
        verify(SGX_ROOT_CA_DN == rootCert.subjectX500Principal, SGX_ROOT_CA_MISSING)
        verify(SGX_TCB_SIGNING_DN == tcbSigningCert.subjectX500Principal, SGX_TCB_SIGNING_CERT_MISSING)

        verifyAgainstCrl(rootCert, rootCaCrl)
        verify(!rootCaCrl.isRevoked(tcbSigningCert), SGX_TCB_SIGNING_CERT_REVOKED)
    }

    private fun validateCertPath(chain: CertPath, collateralVersion: String) {
        val certTime = chain.x509Certs.minOf { it.notAfter }
        val pkixParameters = PKIXParameters(setOf(TrustAnchor(trustedRootCert(collateralVersion), null))).apply {
            isRevocationEnabled = false
            date = certTime
        }
        CertPathValidator.getInstance("PKIX").validate(chain, pkixParameters)
    }

    private fun verifyAgainstCrl(cert: X509Certificate, crl: X509CRL) {
        val rootCrlIssuer = crl.issuerX500Principal
        val rootSubject = cert.subjectX500Principal
        verify(rootCrlIssuer == rootSubject, SGX_CRL_UNKNOWN_ISSUER)

        // https://boringssl.googlesource.com/boringssl/+/master/include/openssl/nid.h
        // #define NID_crl_number 88
        // #define OBJ_crl_number 2L, 5L, 29L, 20L
        // #define NID_authority_key_identifier 90
        // #define OBJ_authority_key_identifier 2L, 5L, 29L, 35L
        val oids = crl.nonCriticalExtensionOIDs
        verify("2.5.29.20" in oids && "2.5.29.35" in oids, SGX_CRL_INVALID_EXTENSIONS)

        try {
            crl.verify(cert.publicKey)
        } catch (e: GeneralSecurityException) {
            throw VerificationException(SGX_CRL_INVALID_SIGNATURE, e)
        }
    }

    private fun verifyAgainstIssuer(cert: X509Certificate, issuer: X509Certificate) {
        check(cert.issuerX500Principal == issuer.subjectX500Principal)
        cert.verify(issuer.publicKey)
    }

    private fun verifyAttestationKeyAndQeReportDataHash(authData: ByteCursor<SgxEcdsa256BitQuoteAuthData>) {
        val expectedReportBody = Cursor.allocate(SgxReportData).apply {
            // Hash is 32 bytes, and report data is 64 bytes
            val hash = digest("SHA-256") {
                update(authData[ecdsaAttestationKey].buffer)
                update(authData[qeAuthData].read())
            }
            buffer.put(hash)
        }

        verify(authData[qeReport][reportData] == expectedReportBody, INVALID_QE_REPORT_DATA)
    }

    /// QuoteVerification/QVL/Src/AttestationLibrary/src/Verifiers/EnclaveReportVerifier.cpp:47
    private fun verifyEnclaveReport(
        enclaveReportBody: ByteCursor<SgxReportBody>,
        enclaveIdentity: EnclaveIdentity
    ): EnclaveReportStatus? {
        // enclave report vs enclave identify json
        // (only used with QE and QE Identity, actually)

        // miscselectMask and miscselect from the enclave identity are in big-endian whilst the miscSelect from the
        // enclave report body is in little-endian.
        val miscselectMask = enclaveIdentity.miscselectMask.buffer().getUnsignedInt()
        val miscselect = enclaveIdentity.miscselect.buffer().getUnsignedInt()
        if ((enclaveReportBody[miscSelect].read() and miscselectMask) != miscselect) {
            return MISCSELECT_MISMATCH
        }

        val attributes = enclaveIdentity.attributes
        val attributesMask = enclaveIdentity.attributesMask
        val reportAttributes = enclaveReportBody[SgxReportBody.attributes].bytes
        for (i in 0..7) {
            if ((reportAttributes[i].toInt() and attributesMask[i].toInt()) != attributes[i].toInt())
                return ATTRIBUTES_MISMATCH
        }

        if (enclaveReportBody[mrsigner].read() != enclaveIdentity.mrsigner.buffer()) {
            return MRSIGNER_MISMATCH
        }

        if (enclaveReportBody[isvProdId].read() != enclaveIdentity.isvprodid) {
            return ISVPRODID_MISMATCH
        }

        val isvSvn = enclaveReportBody[isvSvn].read()
        val tcbStatus = getTcbStatus(isvSvn, enclaveIdentity.tcbLevels)
        if (tcbStatus != EnclaveTcbStatus.UpToDate) {
            return if (tcbStatus == EnclaveTcbStatus.Revoked) ISVSVN_REVOKED else ISVSVN_OUT_OF_DATE
        }

        return null
    }

    private enum class EnclaveReportStatus {
        MISCSELECT_MISMATCH,
        ATTRIBUTES_MISMATCH,
        MRSIGNER_MISMATCH,
        ISVPRODID_MISMATCH,
        ISVSVN_REVOKED,
        ISVSVN_OUT_OF_DATE
    }

    private fun convergeTcbStatus(tcbLevelStatus: TcbStatus, qeStatus: EnclaveReportStatus?): TcbStatus {
        if (qeStatus === ISVSVN_OUT_OF_DATE) {
            if (tcbLevelStatus === TcbStatus.UpToDate ||
                tcbLevelStatus === TcbStatus.SWHardeningNeeded
            ) {
                return TcbStatus.OutOfDate
            }
            if (tcbLevelStatus === TcbStatus.ConfigurationNeeded ||
                tcbLevelStatus === TcbStatus.ConfigurationAndSWHardeningNeeded
            ) {
                return TcbStatus.OutOfDateConfigurationNeeded
            }
        }
        if (qeStatus === ISVSVN_REVOKED) {
            return TcbStatus.Revoked
        }

        return tcbLevelStatus
    }

    private fun getTcbStatus(isvSvn: Int, levels: List<EnclaveTcbLevel>): EnclaveTcbStatus {
        for (lvl in levels) {
            if (lvl.tcb.isvsvn <= isvSvn)
                return lvl.tcbStatus
        }
        return EnclaveTcbStatus.Revoked
    }

    private fun getMatchingTcbLevel(pckExtensions: SGXExtensionASN1Parser, tcbInfo: TcbInfo): TcbStatus {
        val pckTcbs = IntArray(16) { pckExtensions.getInt("${SGX_TCB_OID}.${it + 1}") }
        val pckPceSvn = pckExtensions.getInt(SGX_PCESVN_OID)

        for (lvl in tcbInfo.tcbLevels) {
            if (isCpuSvnHigherOrEqual(pckTcbs, lvl.tcb) && pckPceSvn >= lvl.tcb.pcesvn) {
                return lvl.tcbStatus
            }
        }

        throw GeneralSecurityException("TCB_NOT_SUPPORTED")
    }

    private fun isCpuSvnHigherOrEqual(pckTcb: IntArray, jsonTcb: Tcb): Boolean {
        for (j in pckTcb.indices) {
            // If *ANY* CPUSVN component is lower then CPUSVN is considered lower
            if (pckTcb[j] < getSgxTcbComponentSvn(jsonTcb, j)) return false
        }
        // but for CPUSVN to be considered higher it requires that *EVERY* CPUSVN component to be higher or equal
        return true
    }

    private fun checkTcbLevel(pckExtensions: SGXExtensionASN1Parser, tcbInfo: TcbInfo): TcbStatus {
        val tcbLevelStatus = getMatchingTcbLevel(pckExtensions, tcbInfo)
        check(tcbInfo.version == 2 || tcbLevelStatus != TcbStatus.OutOfDateConfigurationNeeded) {
            "TCB_UNRECOGNIZED_STATUS"
        }
        return tcbLevelStatus
    }

    private fun getSgxTcbComponentSvn(tcb: Tcb, index: Int): Int {
        // 0 base index
        return when (index) {
            0 -> tcb.sgxtcbcomp01svn
            1 -> tcb.sgxtcbcomp02svn
            2 -> tcb.sgxtcbcomp03svn
            3 -> tcb.sgxtcbcomp04svn
            4 -> tcb.sgxtcbcomp05svn
            5 -> tcb.sgxtcbcomp06svn
            6 -> tcb.sgxtcbcomp07svn
            7 -> tcb.sgxtcbcomp08svn
            8 -> tcb.sgxtcbcomp09svn
            9 -> tcb.sgxtcbcomp10svn
            10 -> tcb.sgxtcbcomp11svn
            11 -> tcb.sgxtcbcomp12svn
            12 -> tcb.sgxtcbcomp13svn
            13 -> tcb.sgxtcbcomp14svn
            14 -> tcb.sgxtcbcomp15svn
            15 -> tcb.sgxtcbcomp16svn
            else -> throw GeneralSecurityException("Invalid sgxtcbcompsvn index $index")
        }
    }

    private fun verify(check: Boolean, status: ErrorStatus) {
        if (!check) throw VerificationException(status)
    }

    private class VerificationException(val status: ErrorStatus, cause: Throwable? = null) : Exception(cause)
}

interface VerificationStatus
