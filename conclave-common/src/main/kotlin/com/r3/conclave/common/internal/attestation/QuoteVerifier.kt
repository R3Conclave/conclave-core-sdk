package com.r3.conclave.common.internal.attestation

import com.r3.conclave.common.OpaqueBytes
import com.r3.conclave.common.internal.*
import com.r3.conclave.common.internal.SgxEcdsa256BitQuoteAuthData.ecdsa256BitSignature
import com.r3.conclave.common.internal.SgxEcdsa256BitQuoteAuthData.ecdsaAttestationKey
import com.r3.conclave.common.internal.SgxEcdsa256BitQuoteAuthData.qeAuthData
import com.r3.conclave.common.internal.SgxEcdsa256BitQuoteAuthData.qeCertData
import com.r3.conclave.common.internal.SgxEcdsa256BitQuoteAuthData.qeReport
import com.r3.conclave.common.internal.SgxEcdsa256BitQuoteAuthData.qeReportSignature
import com.r3.conclave.common.internal.SgxQuote.signType
import com.r3.conclave.common.internal.SgxQuote.version
import com.r3.conclave.common.internal.SgxReportBody.isvProdId
import com.r3.conclave.common.internal.SgxReportBody.isvSvn
import com.r3.conclave.common.internal.SgxReportBody.miscSelect
import com.r3.conclave.common.internal.SgxReportBody.mrsigner
import com.r3.conclave.common.internal.SgxReportBody.reportData
import com.r3.conclave.common.internal.attestation.DCAPUtils.parseRawEcdsaToDerEncoding
import com.r3.conclave.common.internal.attestation.QuoteVerifier.Status.*
import com.r3.conclave.utilities.internal.digest
import com.r3.conclave.utilities.internal.getUnsignedInt
import com.r3.conclave.utilities.internal.x509Certs
import java.security.GeneralSecurityException
import java.security.Signature
import java.security.SignatureException
import java.security.cert.*
import java.time.Instant
import java.util.*

object QuoteVerifier {

    enum class Status {
        OK,
        SGX_ENCLAVE_REPORT_ISVSVN_OUT_OF_DATE,
        SGX_ENCLAVE_REPORT_MISCSELECT_MISMATCH,
        SGX_ENCLAVE_REPORT_ATTRIBUTES_MISMATCH,
        SGX_ENCLAVE_REPORT_MRSIGNER_MISMATCH,
        SGX_ENCLAVE_REPORT_ISVPRODID_MISMATCH,
        SGX_ENCLAVE_REPORT_ISVSVN_REVOKED,
        INVALID_PCK_CERT,
        PCK_REVOKED,
        INVALID_PCK_CRL,
        TCB_INFO_MISMATCH,
        TCB_OUT_OF_DATE_CONFIGURATION_NEEDED,
        TCB_OUT_OF_DATE,
        TCB_REVOKED,
        TCB_SW_HARDENING_NEEDED,
        TCB_CONFIGURATION_NEEDED,
        TCB_CONFIGURATION_AND_SW_HARDENING_NEEDED,
        TCB_UNRECOGNIZED_STATUS,
        UNSUPPORTED_CERT_FORMAT,
        SGX_ROOT_CA_MISSING,
        SGX_INTERMEDIATE_CA_MISSING,
        SGX_PCK_MISSING,
        SGX_ROOT_CA_INVALID_ISSUER,
        SGX_INTERMEDIATE_CA_INVALID_ISSUER,
        SGX_PCK_INVALID_ISSUER,
        TRUSTED_ROOT_CA_INVALID,
        SGX_PCK_CERT_CHAIN_UNTRUSTED,
        SGX_CRL_UNKNOWN_ISSUER,
        SGX_CRL_INVALID_EXTENSIONS,
        SGX_CRL_INVALID_SIGNATURE,
        SGX_INTERMEDIATE_CA_REVOKED,
        SGX_PCK_REVOKED,
        SGX_PCK_CERT_CHAIN_EXPIRED,
        SGX_CRL_EXPIRED,
        SGX_TCB_SIGNING_CERT_MISSING,
        SGX_TCB_SIGNING_CERT_INVALID_ISSUER,
        SGX_SIGNING_CERT_CHAIN_EXPIRED,
        SGX_TCB_INFO_EXPIRED,
        SGX_TCB_SIGNING_CERT_REVOKED,
        SGX_TCB_SIGNING_CERT_CHAIN_UNTRUSTED,
        TCB_INFO_INVALID_SIGNATURE,
        SGX_ENCLAVE_IDENTITY_EXPIRED,
        SGX_ENCLAVE_IDENTITY_INVALID_SIGNATURE,
        INVALID_QE_REPORT_DATA,
        SGX_ENCLAVE_REPORT_UNSUPPORTED_FORMAT,
        UNSUPPORTED_QUOTE_FORMAT,
        SGX_ENCLAVE_IDENTITY_UNSUPPORTED_FORMAT,
        SGX_ENCLAVE_IDENTITY_INVALID,
        SGX_ENCLAVE_IDENTITY_UNSUPPORTED_VERSION,
        UNSUPPORTED_QE_IDENTITY_FORMAT,
        QE_IDENTITY_MISMATCH,
        INVALID_QE_REPORT_SIGNATURE,
        INVALID_QUOTE_SIGNATURE
    }

    private const val SGX_EXTENSION_OID = "1.2.840.113741.1.13.1"
    private const val SGX_EXTENSION_FMSPC_OID = "1.2.840.113741.1.13.1.4"
    private const val SGX_EXTENSION_PCEID_OID = "1.2.840.113741.1.13.1.3"
    private const val SGX_EXTENSION_TCB = "1.2.840.113741.1.13.1.2"
    private const val SGX_EXTENSION_PCESVN = "1.2.840.113741.1.13.1.2.17"
    /// SGX_EXTENSION_PCEID_TCB_LEVEL_N = "1.2.840.113741.1.13.1.2.[1..16]"

    private const val QUOTE_VERSION = 3

    private const val SGX_ROOT_CA_CN_PHRASE = "SGX Root CA"
    private const val SGX_INTERMEDIATE_CN_PHRASE = "CA"
    private const val SGX_PCK_CN_PHRASE = "SGX PCK Certificate"
    private const val SGX_TCB_SIGNING_CN_PHRASE = "SGX TCB Signing"

    // TODO The quote and signature parameters can be combined by using a single ByteCursor<SgxSignedQuote> parameter.
    // QuoteVerification/QvE/Enclave/qve.cpp:sgx_qve_verify_quote
    fun verify(quote: ByteCursor<SgxQuote>, signature: ByteArray, collateral: QuoteCollateral): Triple<Status, Instant, Boolean> {
        // See page 48 at https://download.01.org/intel-sgx/sgx-dcap/1.8/linux/docs/Intel_SGX_ECDSA_QuoteLibReference_DCAP_API.pdf
        if (quote[signType].read() != SgxQuoteSignType.ECDSA_P256)
            throw GeneralSecurityException("Invalid quote $UNSUPPORTED_QUOTE_FORMAT")

        val authData = Cursor.wrap(SgxEcdsa256BitQuoteAuthData, signature)

        val trustedRootCA = loadTrustedRootCA()

        val pckCertPath = authData[qeCertData].toPckCertPath()

        var latestIssueDate = getLatestIssueDate(trustedRootCA, Instant.MIN)
        latestIssueDate = getLatestIssueDate(pckCertPath, latestIssueDate)
        latestIssueDate = getLatestIssueDate(collateral.rootCaCrl, latestIssueDate)
        latestIssueDate = getLatestIssueDate(collateral.pckCrl, latestIssueDate)
        latestIssueDate = getLatestIssueDate(collateral.tcbInfoIssuerChain, latestIssueDate)
        latestIssueDate = getLatestIssueDate(collateral.signedTcbInfo.tcbInfo, latestIssueDate)
        latestIssueDate = getLatestIssueDate(collateral.qeIdentityIssuerChain, latestIssueDate)
        latestIssueDate = getLatestIssueDate(collateral.signedQeIdentity.enclaveIdentity, latestIssueDate)

        var earliestExpireDate = getEarliestExpirationDate(trustedRootCA, Instant.MAX)
        earliestExpireDate = getEarliestExpirationDate(pckCertPath, earliestExpireDate)
        earliestExpireDate = getEarliestExpirationDate(collateral.rootCaCrl, earliestExpireDate)
        earliestExpireDate = getEarliestExpirationDate(collateral.pckCrl, earliestExpireDate)
        earliestExpireDate = getEarliestExpirationDate(collateral.tcbInfoIssuerChain, earliestExpireDate)
        earliestExpireDate = getEarliestExpirationDate(collateral.signedTcbInfo.tcbInfo, earliestExpireDate)
        earliestExpireDate = getEarliestExpirationDate(collateral.qeIdentityIssuerChain, earliestExpireDate)
        earliestExpireDate = getEarliestExpirationDate(collateral.signedQeIdentity.enclaveIdentity, earliestExpireDate)

        val collateralHasExpired = earliestExpireDate <= latestIssueDate

        val pckCertVerificationStatus = verifyPckCertificate(pckCertPath, collateral, trustedRootCA, latestIssueDate)
        if (pckCertVerificationStatus != OK && !isExpirationError(pckCertVerificationStatus))
            return Triple(pckCertVerificationStatus, latestIssueDate, collateralHasExpired)

        val tcbInfoVerificationStatus = verifyTcbInfo(collateral, trustedRootCA, latestIssueDate)
        if (tcbInfoVerificationStatus != OK && !isExpirationError(tcbInfoVerificationStatus))
            return Triple(tcbInfoVerificationStatus, latestIssueDate, collateralHasExpired)

        val qeIdentityVerificationStatus = verifyQeIdentity(collateral, trustedRootCA, latestIssueDate)
        if (qeIdentityVerificationStatus != OK && !isExpirationError(qeIdentityVerificationStatus))
            return Triple(qeIdentityVerificationStatus, latestIssueDate, collateralHasExpired)

        val quoteVerificationStatus = verifyQuote(
                quote,
                authData,
                pckCertPath.x509Certs[0],
                collateral.pckCrl,
                collateral.signedTcbInfo.tcbInfo,
                collateral.signedQeIdentity.enclaveIdentity
        )

        return when (quoteVerificationStatus) {
            OK,
            TCB_SW_HARDENING_NEEDED,
            TCB_CONFIGURATION_AND_SW_HARDENING_NEEDED,
            TCB_CONFIGURATION_NEEDED,
            TCB_OUT_OF_DATE,
            TCB_OUT_OF_DATE_CONFIGURATION_NEEDED -> Triple(quoteVerificationStatus, latestIssueDate, collateralHasExpired)
            else -> throw GeneralSecurityException("Invalid quote $quoteVerificationStatus")
            /*
            7. SGX_QL_QV_RESULT_INVALID_SIGNATURE – Terminal
            8. SGX_QL_QV_RESULT_REVOKED – Terminal
            9. SGX_QL_QV_RESULT_UNSPECIFIED – Terminal
             */
        }
    }

    private fun isExpirationError(status: Status): Boolean {
        return when (status) {
            SGX_TCB_INFO_EXPIRED,
            SGX_PCK_CERT_CHAIN_EXPIRED,
            SGX_CRL_EXPIRED,
            SGX_SIGNING_CERT_CHAIN_EXPIRED,
            SGX_ENCLAVE_IDENTITY_EXPIRED -> true
            else -> false
        }
    }

    /// QuoteVerification/QVL/Src/AttestationLibrary/src/Verifiers/QuoteVerifier.cpp:176
    private fun verifyQuote(
            quote: ByteCursor<SgxQuote>,
            authData: ByteCursor<SgxEcdsa256BitQuoteAuthData>,
            pckCert: X509Certificate,
            pckCrl: X509CRL,
            tcbInfo: TcbInfo,
            qeIdentity: EnclaveIdentity
    ): Status {
        /// 4.1.2.4.2
        if (quote[version].read() != QUOTE_VERSION)
            return UNSUPPORTED_QUOTE_FORMAT

        /// 4.1.2.4.4
        if (!pckCert.subjectDN.name.contains(SGX_PCK_CN_PHRASE))
            return INVALID_PCK_CERT

        /// 4.1.2.4.6
        if (!pckCrl.issuerDN.name.contains(SGX_INTERMEDIATE_CN_PHRASE))
            return INVALID_PCK_CRL

        /// 4.1.2.4.6
        if (pckCrl.issuerDN.name != pckCert.issuerDN.name)
            return INVALID_PCK_CRL

        /// 4.1.2.4.7
        if (pckCrl.isRevoked(pckCert))
            return PCK_REVOKED

        /// 4.1.2.4.9
        val tcbs = IntArray(16)
        val (fmspc, pceid, pcesvn) = getFmspcAndPceIdAndTcbs(pckCert, tcbs) // from pck cert chain (from quote)

        if (OpaqueBytes(fmspc) != tcbInfo.fmspc)
            return TCB_INFO_MISMATCH

        if (OpaqueBytes(pceid) != tcbInfo.pceId)
            return TCB_INFO_MISMATCH

        /// 4.1.2.4.13
        if (!validateQeReportSignature(authData, pckCert))
            return INVALID_QE_REPORT_SIGNATURE

        /// 4.1.2.4.14
        if (!checkAttestationKeyAndQeReportDataHash(authData))
            return INVALID_QE_REPORT_DATA

        /// 4.1.2.4.15
        val qeIdentityStatus = verifyEnclaveReport(authData[qeReport], qeIdentity)
        when (qeIdentityStatus) {
            SGX_ENCLAVE_REPORT_UNSUPPORTED_FORMAT -> return UNSUPPORTED_QUOTE_FORMAT

            SGX_ENCLAVE_IDENTITY_UNSUPPORTED_FORMAT,
            SGX_ENCLAVE_IDENTITY_INVALID,
            SGX_ENCLAVE_IDENTITY_UNSUPPORTED_VERSION -> return UNSUPPORTED_QE_IDENTITY_FORMAT

            SGX_ENCLAVE_REPORT_MISCSELECT_MISMATCH,
            SGX_ENCLAVE_REPORT_ATTRIBUTES_MISMATCH,
            SGX_ENCLAVE_REPORT_MRSIGNER_MISMATCH,
            SGX_ENCLAVE_REPORT_ISVPRODID_MISMATCH -> return QE_IDENTITY_MISMATCH

            else -> {}
        }

        /// 4.1.2.4.16
        if (!verifyIsvReportSignature(authData, quote))
            return INVALID_QUOTE_SIGNATURE

        /// 4.1.2.4.17
        val tcbLevelStatus = checkTcbLevel(tcbs, tcbInfo, SGXExtensionASN1Parser.intValue(pcesvn))
        return convergeTcbStatus(tcbLevelStatus, qeIdentityStatus)
    }

    private fun verifyIsvReportSignature(authData: ByteCursor<SgxEcdsa256BitQuoteAuthData>, quote: ByteCursor<SgxQuote>): Boolean {
        Signature.getInstance("SHA256withECDSA").apply {
            initVerify(authData[ecdsaAttestationKey].toPublicKey())
            update(quote.buffer)
            return verify(authData[ecdsa256BitSignature].toDerEncoding())
        }
    }

    private fun validateQeReportSignature(authData: ByteCursor<SgxEcdsa256BitQuoteAuthData>, pckCert: X509Certificate): Boolean {
        Signature.getInstance("SHA256withECDSA").apply {
            initVerify(pckCert)
            update(authData[qeReport].buffer)
            return verify(authData[qeReportSignature].toDerEncoding())
        }
    }

    /// QuoteVerification/QVL/Src/AttestationLibrary/src/QuoteVerification.cpp:77 - parsing inputs
    /// QuoteVerification/QVL/Src/AttestationLibrary/src/Verifiers/PckCertVerifier.cpp:51 - verification
    /// affectively, a modified/extended version of java.security.CertPathValidator:
    /// pckCertChain here includes rootCert,
    /// rootCert does not have CRL info, so CertPathValidator will fail the revocation check
    private fun verifyPckCertificate(pckCertPath: CertPath, collateral: QuoteCollateral, trustedRootCaCert: X509Certificate, currentTime: Instant): Status {
        if (pckCertPath.certificates.size != 3)
            return UNSUPPORTED_CERT_FORMAT

        val (pck, intermed, root) = pckCertPath.x509Certs

        if (!root.subjectDN.name.contains(SGX_ROOT_CA_CN_PHRASE))
            return SGX_ROOT_CA_MISSING

        if (!intermed.subjectDN.name.contains(SGX_INTERMEDIATE_CN_PHRASE))
            return SGX_INTERMEDIATE_CA_MISSING

        if (!pck.subjectDN.name.contains(SGX_PCK_CN_PHRASE))
            return SGX_PCK_MISSING

        // meaning 'root' is self-signed
        if (!verifyWithIssuer(root, root))
            return SGX_ROOT_CA_INVALID_ISSUER

        // meaning 'root' is signed with 'trusted root'
        // if all good, root and trusted root are actually the same certificate
        if (!verifyWithIssuer(root, trustedRootCaCert))
            return SGX_ROOT_CA_INVALID_ISSUER

        if (!verifyWithIssuer(intermed, root))
            return SGX_INTERMEDIATE_CA_INVALID_ISSUER

        if (!verifyWithIssuer(pck, intermed))
            return SGX_PCK_INVALID_ISSUER

        if (!verifyWithIssuer(trustedRootCaCert, trustedRootCaCert))
            return TRUSTED_ROOT_CA_INVALID

        if (!Arrays.equals(root.signature, trustedRootCaCert.signature))
            return SGX_PCK_CERT_CHAIN_UNTRUSTED

        val checkRootCaCrlCorrectness = verifyWithCrl(root, collateral.rootCaCrl)
        if (checkRootCaCrlCorrectness != OK)
            return checkRootCaCrlCorrectness

        val checkIntermediateCrlCorrectness = verifyWithCrl(intermed, collateral.pckCrl)
        if (checkIntermediateCrlCorrectness != OK)
            return checkIntermediateCrlCorrectness

        if (collateral.rootCaCrl.isRevoked(intermed))
            return SGX_INTERMEDIATE_CA_REVOKED

        if (collateral.pckCrl.isRevoked(pck))
            return SGX_PCK_REVOKED

        try {
            val dt = Date.from(currentTime)
            root.checkValidity(dt)
            intermed.checkValidity(dt)
            pck.checkValidity(dt)
        } catch (e: CertificateExpiredException) {
            return SGX_PCK_CERT_CHAIN_EXPIRED
        }

        if (currentTime.isAfter(collateral.rootCaCrl.nextUpdate.toInstant()))
            return SGX_CRL_EXPIRED

        if (currentTime.isAfter(collateral.pckCrl.nextUpdate.toInstant()))
            return SGX_CRL_EXPIRED

        return OK
    }

    /// QuoteVerification/QVL/Src/AttestationLibrary/src/QuoteVerification.cpp:64 - parsing inputs
    /// QuoteVerification/QVL/Src/AttestationLibrary/src/Verifiers/TCBInfoVerifier.cpp:59
    /// QuoteVerification/QVL/Src/AttestationLibrary/src/Verifiers/TCBSigningChain.cpp:51
    private fun verifyTcbInfo(collateral: QuoteCollateral, trustedRootCaCert: X509Certificate, currentTime: Instant): Status {
        val tcbChainVerificationResult = verifyTcbChain(collateral.tcbInfoIssuerChain, collateral.rootCaCrl, trustedRootCaCert)
        if (tcbChainVerificationResult != OK)
            return tcbChainVerificationResult

        val (tcbSigningCert, rootCert) = collateral.tcbInfoIssuerChain.x509Certs

        val signatureResult = verifyJsonSignature(
                collateral.rawSignedTcbInfo,
                """{"tcbInfo":""",
                collateral.signedTcbInfo.signature,
                collateral.tcbInfoIssuerChain.x509Certs[0]
        )
        if (!signatureResult)
            return TCB_INFO_INVALID_SIGNATURE

        try {
            val dt = Date.from(currentTime)
            rootCert.checkValidity(dt)
            tcbSigningCert.checkValidity(dt)
        } catch (e: CertificateExpiredException) {
            return SGX_SIGNING_CERT_CHAIN_EXPIRED
        }

        if (currentTime.isAfter(collateral.rootCaCrl.nextUpdate.toInstant()))
            return SGX_CRL_EXPIRED

        if (currentTime.isAfter(collateral.signedTcbInfo.tcbInfo.nextUpdate)) {
            return SGX_TCB_INFO_EXPIRED
        }

        return OK
    }

    /// QuoteVerification/QVL/Src/AttestationLibrary/src/QuoteVerification.cpp:232
    /// QuoteVerification/QVL/Src/AttestationLibrary/src/Verifiers/EnclaveIdentityVerifier.cpp:60
    private fun verifyQeIdentity(collateral: QuoteCollateral, trustedRootCaCert: X509Certificate, currentTime: Instant): Status {
        // yes, verifyTcbChain is used to verify qeIdentityIssuerChain
        val qeIdentityIssuerChainVerificationResult = verifyTcbChain(collateral.qeIdentityIssuerChain, collateral.rootCaCrl, trustedRootCaCert)
        if (qeIdentityIssuerChainVerificationResult != OK)
            return qeIdentityIssuerChainVerificationResult

        val (signingCert, rootCert) = collateral.qeIdentityIssuerChain.x509Certs

        val signatureResult = verifyJsonSignature(
                collateral.rawSignedQeIdentity,
                """{"enclaveIdentity":""",
                collateral.signedQeIdentity.signature,
                collateral.qeIdentityIssuerChain.x509Certs[0]
        )
        if (!signatureResult)
            return SGX_ENCLAVE_IDENTITY_INVALID_SIGNATURE

        try {
            val dt = Date.from(currentTime)
            rootCert.checkValidity(dt)
            signingCert.checkValidity(dt)
        } catch (e: CertificateExpiredException) {
            return SGX_SIGNING_CERT_CHAIN_EXPIRED
        }

        if (currentTime.isAfter(collateral.rootCaCrl.nextUpdate.toInstant()))
            return SGX_CRL_EXPIRED

        if (currentTime.isAfter(collateral.signedQeIdentity.enclaveIdentity.nextUpdate))
            return SGX_ENCLAVE_IDENTITY_EXPIRED

        return OK
    }

    private fun verifyJsonSignature(rawJson: String, prefix: String, signature: OpaqueBytes, cert: X509Certificate): Boolean {
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
        return verifier.verify(parseRawEcdsaToDerEncoding(signature.buffer()))
    }

    /// QuoteVerification/QVL/Src/AttestationLibrary/src/Verifiers/TCBSigningChain.cpp:56
    private fun verifyTcbChain(tcbSignChain: CertPath, rootCaCrl: X509CRL, trustedRootCaCert: X509Certificate): Status {
        if (tcbSignChain.certificates.size != 2)
            return UNSUPPORTED_CERT_FORMAT

        val (tcbSigningCert, rootCert) = tcbSignChain.x509Certs

        if (!rootCert.subjectDN.name.contains(SGX_ROOT_CA_CN_PHRASE))
            return SGX_ROOT_CA_MISSING

        if (!verifyWithIssuer(rootCert, rootCert))
            return SGX_ROOT_CA_INVALID_ISSUER

        if (!verifyWithIssuer(rootCert, trustedRootCaCert))
            return SGX_ROOT_CA_INVALID_ISSUER

        if (!tcbSigningCert.subjectDN.name.contains(SGX_TCB_SIGNING_CN_PHRASE))
            return SGX_TCB_SIGNING_CERT_MISSING

        if (!verifyWithIssuer(tcbSigningCert, rootCert))
            return SGX_TCB_SIGNING_CERT_INVALID_ISSUER

        val crlVerificationStatus = verifyWithCrl(rootCert, rootCaCrl)
        if (crlVerificationStatus != OK)
            return crlVerificationStatus

        if (rootCaCrl.isRevoked(tcbSigningCert))
            return SGX_TCB_SIGNING_CERT_REVOKED

        if (trustedRootCaCert.subjectDN.name != trustedRootCaCert.issuerDN.name)
            return TRUSTED_ROOT_CA_INVALID

        if (!Arrays.equals(rootCert.signature, trustedRootCaCert.signature))
            return SGX_TCB_SIGNING_CERT_CHAIN_UNTRUSTED

        return OK
    }

    private fun loadTrustedRootCA(): X509Certificate {
        return javaClass.getResourceAsStream("/DCAPAttestationReportSigningCACert.pem").use {
            CertificateFactory.getInstance("X.509").generateCertificate(it) as X509Certificate
        }
    }

    private fun verifyWithCrl(cert: X509Certificate, crl: X509CRL): Status {
        val rootCrlIssuer = crl.issuerX500Principal
        val rootSubject = cert.subjectX500Principal
        if (rootCrlIssuer.name != rootSubject.name)
            return SGX_CRL_UNKNOWN_ISSUER

        // https://boringssl.googlesource.com/boringssl/+/master/include/openssl/nid.h
        // #define NID_crl_number 88
        // #define OBJ_crl_number 2L, 5L, 29L, 20L
        // #define NID_authority_key_identifier 90
        // #define OBJ_authority_key_identifier 2L, 5L, 29L, 35L
        val oids = crl.nonCriticalExtensionOIDs
        if (!oids.contains("2.5.29.20") || !oids.contains("2.5.29.35"))
            return SGX_CRL_INVALID_EXTENSIONS

        try {
            crl.verify(cert.publicKey)
        } catch (ex: GeneralSecurityException) {
            return SGX_CRL_INVALID_SIGNATURE
        }

        return OK
    }

    private fun verifyWithIssuer(cert: X509Certificate, issuer: X509Certificate): Boolean {
        if (cert.issuerDN.name != issuer.subjectDN.name)
            return false

        try {
            cert.verify(issuer.publicKey)
        } catch (ex: SignatureException) {
            return false
        }

        return true
    }

    private fun checkAttestationKeyAndQeReportDataHash(authData: ByteCursor<SgxEcdsa256BitQuoteAuthData>): Boolean {
        val expectedReportBody = Cursor.allocate(SgxReportData).apply {
            // Hash is 32 bytes, and report data is 64 bytes
            val hash = digest("SHA-256") {
                update(authData[ecdsaAttestationKey].buffer)
                update(authData[qeAuthData].read())
            }
            buffer.put(hash)
        }

        return authData[qeReport][reportData] == expectedReportBody
    }

    /// QuoteVerification/QVL/Src/AttestationLibrary/src/Verifiers/EnclaveReportVerifier.cpp:47
    private fun verifyEnclaveReport(enclaveReportBody: ByteCursor<SgxReportBody>, enclaveIdentity: EnclaveIdentity): Status {
        // enclave report vs enclave identify json
        // (only used with QE and QE Identity, actually)

        /// 4.1.2.9.5
        // miscselectMask and miscselect from the enclave identity are in big-endian whilst the miscSelect from the
        // enclave report body is in little-endian.
        val miscselectMask = enclaveIdentity.miscselectMask.buffer().getUnsignedInt()
        val miscselect = enclaveIdentity.miscselect.buffer().getUnsignedInt()
        if ((enclaveReportBody[miscSelect].read() and miscselectMask) != miscselect) {
            return SGX_ENCLAVE_REPORT_MISCSELECT_MISMATCH
        }

        /// 4.1.2.9.6
        val attributes = enclaveIdentity.attributes
        val attributesMask = enclaveIdentity.attributesMask
        val reportAttributes = enclaveReportBody[SgxReportBody.attributes].bytes
        for (i in 0..7) {
            if ((reportAttributes[i].toInt() and attributesMask[i].toInt()) != attributes[i].toInt())
                return SGX_ENCLAVE_REPORT_ATTRIBUTES_MISMATCH
        }

        /// 4.1.2.9.8
        if (enclaveReportBody[mrsigner].read() != enclaveIdentity.mrsigner.buffer()) {
            return SGX_ENCLAVE_REPORT_MRSIGNER_MISMATCH
        }

        /// 4.1.2.9.9
        if (enclaveReportBody[isvProdId].read() != enclaveIdentity.isvprodid) {
            return SGX_ENCLAVE_REPORT_ISVPRODID_MISMATCH
        }

        /// 4.1.2.9.10 & 4.1.2.9.11
        val isvSvn = enclaveReportBody[isvSvn].read()
        val tcbStatus = getTcbStatus(isvSvn, enclaveIdentity.tcbLevels)
        if (tcbStatus != EnclaveTcbStatus.UpToDate) {
            return if (tcbStatus == EnclaveTcbStatus.Revoked) SGX_ENCLAVE_REPORT_ISVSVN_REVOKED
            else SGX_ENCLAVE_REPORT_ISVSVN_OUT_OF_DATE
        }

        return OK
    }

    private fun convergeTcbStatus(tcbLevelStatus: Status, qeTcbStatus: Status): Status {
        if (qeTcbStatus === SGX_ENCLAVE_REPORT_ISVSVN_OUT_OF_DATE) {
            if (tcbLevelStatus === OK ||
                    tcbLevelStatus === TCB_SW_HARDENING_NEEDED) {
                return TCB_OUT_OF_DATE
            }
            if (tcbLevelStatus === TCB_CONFIGURATION_NEEDED ||
                    tcbLevelStatus === TCB_CONFIGURATION_AND_SW_HARDENING_NEEDED) {
                return TCB_OUT_OF_DATE_CONFIGURATION_NEEDED
            }
        }

        if (qeTcbStatus === SGX_ENCLAVE_REPORT_ISVSVN_REVOKED) {
            return TCB_REVOKED
        }

        return when (tcbLevelStatus) {
            TCB_OUT_OF_DATE,
            TCB_REVOKED,
            TCB_CONFIGURATION_NEEDED,
            TCB_OUT_OF_DATE_CONFIGURATION_NEEDED,
            TCB_SW_HARDENING_NEEDED,
            TCB_CONFIGURATION_AND_SW_HARDENING_NEEDED,
            OK -> tcbLevelStatus
            else -> TCB_UNRECOGNIZED_STATUS
        }
    }

    private fun getTcbStatus(isvSvn: Int, levels: List<EnclaveTcbLevel>): EnclaveTcbStatus {
        for (lvl in levels) {
            if (lvl.tcb.isvsvn <= isvSvn)
                return lvl.tcbStatus
        }
        return EnclaveTcbStatus.Revoked
    }

    private fun getMatchingTcbLevel(tcbInfo: TcbInfo, pckTcb: IntArray, pckPceSvn: Int): TcbStatus {
        for (lvl in tcbInfo.tcbLevels) {
            if (isCpuSvnHigherOrEqual(pckTcb, lvl.tcb) && pckPceSvn >= lvl.tcb.pcesvn) {
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

    private fun checkTcbLevel(pckCertTCBs: IntArray, tcbInfo: TcbInfo, pcesvn: Int): Status {
        /// 4.1.2.4.17.1 & 4.1.2.4.17.2
        val tcbLevelStatus = getMatchingTcbLevel(tcbInfo, pckCertTCBs, pcesvn)

        return when {
            tcbLevelStatus == TcbStatus.OutOfDate -> TCB_OUT_OF_DATE
            tcbLevelStatus == TcbStatus.Revoked -> TCB_REVOKED
            tcbLevelStatus == TcbStatus.ConfigurationNeeded -> TCB_CONFIGURATION_NEEDED
            tcbLevelStatus == TcbStatus.ConfigurationAndSWHardeningNeeded -> TCB_CONFIGURATION_AND_SW_HARDENING_NEEDED
            tcbLevelStatus == TcbStatus.UpToDate -> OK
            tcbLevelStatus == TcbStatus.SWHardeningNeeded -> TCB_SW_HARDENING_NEEDED
            tcbInfo.version == 2 && tcbLevelStatus == TcbStatus.OutOfDateConfigurationNeeded -> TCB_OUT_OF_DATE_CONFIGURATION_NEEDED
            else -> throw GeneralSecurityException("TCB_UNRECOGNIZED_STATUS")
        }
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

    private fun getFmspcAndPceIdAndTcbs(pckCert: X509Certificate, tcbs: IntArray): Array<ByteArray> {
        val decoder = SGXExtensionASN1Parser()
        val ext = pckCert.getExtensionValue(SGX_EXTENSION_OID)
        decoder.parse(ext, ext.size)

        for (i in 1..16)
            tcbs[i - 1] = decoder.intValue("$SGX_EXTENSION_TCB.$i")

        return arrayOf(decoder.value(SGX_EXTENSION_FMSPC_OID), decoder.value(SGX_EXTENSION_PCEID_OID), decoder.value(SGX_EXTENSION_PCESVN))
    }

    private fun getLatestIssueDate(identity: EnclaveIdentity, latestIssueDate: Instant): Instant {
        return maxOf(identity.issueDate, latestIssueDate)
    }

    private fun getEarliestExpirationDate(identity: EnclaveIdentity, earliestExpireDate: Instant): Instant {
        return minOf(identity.nextUpdate, earliestExpireDate)
    }

    private fun getLatestIssueDate(tcb: TcbInfo, latestIssueDate: Instant): Instant {
        return maxOf(tcb.issueDate, latestIssueDate)
    }

    private fun getEarliestExpirationDate(tcb: TcbInfo, earliestExpireDate: Instant): Instant {
        return minOf(tcb.nextUpdate, earliestExpireDate)
    }

    private fun getLatestIssueDate(crl: X509CRL, latestIssueDate: Instant): Instant {
        return maxOf(crl.thisUpdate.toInstant(), latestIssueDate)
    }

    private fun getEarliestExpirationDate(crl: X509CRL, earliestExpireDate: Instant): Instant {
        return minOf(crl.nextUpdate.toInstant(), earliestExpireDate)
    }

    private fun getLatestIssueDate(cert: X509Certificate, latestIssueDate: Instant): Instant {
        return maxOf(cert.notBefore.toInstant(), latestIssueDate)
    }

    private fun getLatestIssueDate(chain: CertPath, latestIssueDate: Instant): Instant {
        var latestIssueDateOut = latestIssueDate
        for (cert in chain.x509Certs) latestIssueDateOut = getLatestIssueDate(cert, latestIssueDateOut)
        return latestIssueDateOut
    }

    private fun getEarliestExpirationDate(cert: X509Certificate, earliestExpireDate: Instant): Instant {
        return minOf(cert.notAfter.toInstant(), earliestExpireDate)
    }

    private fun getEarliestExpirationDate(certPath: CertPath, earliestExpireDate: Instant): Instant {
        var earliestExpireDateOut = earliestExpireDate
        for (cert in certPath.x509Certs) earliestExpireDateOut = getEarliestExpirationDate(cert, earliestExpireDateOut)
        return earliestExpireDateOut
    }
}
