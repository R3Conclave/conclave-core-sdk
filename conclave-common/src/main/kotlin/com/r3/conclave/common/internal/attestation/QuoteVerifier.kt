package com.r3.conclave.common.internal.attestation

import com.fasterxml.jackson.databind.ObjectMapper
import com.r3.conclave.common.SHA256Hash
import com.r3.conclave.common.internal.Cursor
import com.r3.conclave.common.internal.ECDSASignature
import com.r3.conclave.common.internal.SGXExtensionASN1Parser
import com.r3.conclave.common.internal.SgxReportBody
import com.r3.conclave.common.internal.attestation.QuoteVerifier.Status.*
import com.r3.conclave.utilities.internal.parseHex
import com.r3.conclave.utilities.internal.toHexString
import java.io.ByteArrayInputStream
import java.math.BigInteger
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
    private const val QUOTE_KEY_TYPE = 2

    private const val SGX_ROOT_CA_CN_PHRASE = "SGX Root CA"
    private const val SGX_INTERMEDIATE_CN_PHRASE = "CA"
    private const val SGX_PCK_CN_PHRASE = "SGX PCK Certificate"
    private const val SGX_TCB_SIGNING_CN_PHRASE = "SGX TCB Signing"

    fun verify(reportBytes: ByteArray, signature: ByteArray, certPath: CertPath, collateral: QuoteCollateral):
            Triple<Status,Instant,Boolean> {

        val trustedRootCA = loadTrustedRootCA()

        val rootCaCrl = parseCRL(collateral.rootCaCrl)
        val pckCrl = parseCRL(collateral.pckCrl)

        val tcbSignChain = parseCertPath(collateral.tcbInfoIssuerChain)
        val tcbInfo = attestationObjectMapper.readValue(collateral.tcbInfo, TcbInfoSigned::class.java)

        val qeIdentityIssuerChain = parseCertPath(collateral.qeIdentityIssuerChain)
        val qeIdentity = attestationObjectMapper.readValue(collateral.qeIdentity, EnclaveIdentitySigned::class.java)

        var latestIssueDate = getLatestIssueDate(trustedRootCA, Instant.MIN)
        latestIssueDate = getLatestIssueDate(rootCaCrl, latestIssueDate)
        latestIssueDate = getLatestIssueDate(pckCrl, latestIssueDate)
        latestIssueDate = getLatestIssueDate(tcbSignChain, latestIssueDate)
        latestIssueDate = getLatestIssueDate(tcbInfo.tcbInfo, latestIssueDate)
        latestIssueDate = getLatestIssueDate(qeIdentityIssuerChain, latestIssueDate)
        latestIssueDate = getLatestIssueDate(qeIdentity.enclaveIdentity, latestIssueDate)

        var earliestExpireDate = getEarliestExpirationDate(trustedRootCA, Instant.MAX)
        earliestExpireDate = getEarliestExpirationDate(rootCaCrl, earliestExpireDate)
        earliestExpireDate = getEarliestExpirationDate(pckCrl, earliestExpireDate)
        earliestExpireDate = getEarliestExpirationDate(tcbSignChain, earliestExpireDate)
        earliestExpireDate = getEarliestExpirationDate(tcbInfo.tcbInfo, earliestExpireDate)
        earliestExpireDate = getEarliestExpirationDate(qeIdentityIssuerChain, earliestExpireDate)
        earliestExpireDate = getEarliestExpirationDate(qeIdentity.enclaveIdentity, earliestExpireDate)

        val collateralHasExpiredOut = earliestExpireDate <= latestIssueDate

        val pckCertVerificationStatus = verifyPCKCertificate(certPath, rootCaCrl, pckCrl, trustedRootCA, latestIssueDate)
        if (pckCertVerificationStatus != OK && !isExpirationError(pckCertVerificationStatus))
            return Triple(pckCertVerificationStatus, latestIssueDate, collateralHasExpiredOut)

        val tcbInfoVerificationStatus = verifyTCBInfo(tcbInfo, tcbSignChain, rootCaCrl, trustedRootCA, latestIssueDate)
        if (tcbInfoVerificationStatus != OK && !isExpirationError(tcbInfoVerificationStatus))
                return Triple(tcbInfoVerificationStatus, latestIssueDate, collateralHasExpiredOut)

        val qeIdentityVerificationStatus = verifyQeIdentity(qeIdentity, qeIdentityIssuerChain, rootCaCrl, trustedRootCA, latestIssueDate)
        if (qeIdentityVerificationStatus != OK && !isExpirationError(qeIdentityVerificationStatus))
            return Triple(qeIdentityVerificationStatus, latestIssueDate, collateralHasExpiredOut)

        val quoteVerificationStatus = verifyQuote(reportBytes, signature,
                certPath.certificates[0] as X509Certificate, pckCrl, tcbInfo.tcbInfo, qeIdentity.enclaveIdentity)

        return when (quoteVerificationStatus) {
            OK,
            TCB_SW_HARDENING_NEEDED,
            TCB_CONFIGURATION_AND_SW_HARDENING_NEEDED,
            TCB_CONFIGURATION_NEEDED,
            TCB_OUT_OF_DATE,
            TCB_OUT_OF_DATE_CONFIGURATION_NEEDED -> Triple(quoteVerificationStatus, latestIssueDate, collateralHasExpiredOut)
            else -> throw GeneralSecurityException("Invalid quote $quoteVerificationStatus")
            /*
            7. SGX_QL_QV_RESULT_INVALID_SIGNATURE – Terminal
            8. SGX_QL_QV_RESULT_REVOKED – Terminal
            9. SGX_QL_QV_RESULT_UNSPECIFIED – Terminal
             */
        }
    }

    fun isExpirationError(status: Status): Boolean {
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
    private fun verifyQuote(quoteBytes: ByteArray, signature: ByteArray, pckCert: X509Certificate, pckCrl: X509CRL, tcbInfo: TcbInfo, qeIdentity: EnclaveIdentity): Status {
        /// 4.1.2.4.2
        if (getInt16(quoteBytes, 0).toInt() != QUOTE_VERSION)
            return UNSUPPORTED_QUOTE_FORMAT
        /// see page 48 at https://download.01.org/intel-sgx/sgx-dcap/1.8/linux/docs/Intel_SGX_ECDSA_QuoteLibReference_DCAP_API.pdf
        if (getInt16(quoteBytes, 2).toInt() != QUOTE_KEY_TYPE)
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

        if (!Arrays.equals(fmspc, parseHex(tcbInfo.fmspc)))
            return TCB_INFO_MISMATCH

        if (!Arrays.equals(pceid, parseHex(tcbInfo.pceId)))
            return TCB_INFO_MISMATCH

        /// 4.1.2.4.13
        val ecdsa = ECDSASignature(signature)
        if (!validateQeReportSignature(ecdsa, pckCert))
            return INVALID_QE_REPORT_SIGNATURE

        /// 4.1.2.4.14
        if (!checkAttestationKeyAndQeReportDataHash(ecdsa))
            return INVALID_QE_REPORT_DATA

        /// 4.1.2.4.15
        val qeIdentityStatus = verifyEnclaveReport(ecdsa.getQEReport(), qeIdentity);
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
        if (verifyIsvReportSignature(ecdsa, quoteBytes) != OK)
            return INVALID_QUOTE_SIGNATURE

        /// 4.1.2.4.17
        val tcbLevelStatus = checkTcbLevel(tcbs, tcbInfo, SGXExtensionASN1Parser.intValue(pcesvn))
        return convergeTcbStatus(tcbLevelStatus, qeIdentityStatus)
    }

    private fun verifyIsvReportSignature(ecdsa: ECDSASignature, quoteBytes: ByteArray): Status {
        val subjectSignature = ecdsa.getSignature()

        Signature.getInstance("SHA256withECDSA").apply {
            initVerify(ecdsa.getPublicKey())
            update(quoteBytes)
            if (!verify(subjectSignature)) {
                return INVALID_QUOTE_SIGNATURE
            }
        }
        return OK
    }

    private fun validateQeReportSignature(ecdsa: ECDSASignature, pck: X509Certificate): Boolean {
        val qeReport = ecdsa.getQEReport()
        val qeSignature = ecdsa.getQESignature()
        Signature.getInstance("SHA256withECDSA").apply {
            initVerify(pck)
            update(qeReport)
            return verify(qeSignature)
        }
    }

    /// QuoteVerification/QVL/Src/AttestationLibrary/src/QuoteVerification.cpp:77 - parsing inputs
    /// QuoteVerification/QVL/Src/AttestationLibrary/src/Verifiers/PckCertVerifier.cpp:51 - verification
    /// affectively, a modified/extended version of java.security.CertPathValidator:
    /// pckCertChain here includes rootCert,
    /// rootCert does not have CRL info, so CertPathValidator will fail the revocation check
    private fun verifyPCKCertificate(pckCertChain: CertPath, rootCaCrl: X509CRL, intermediateCaCrl: X509CRL, trustedRootCaCert: X509Certificate, currentTime: Instant): Status {
        if (pckCertChain.certificates.size != 3)
            return UNSUPPORTED_CERT_FORMAT

        val root = pckCertChain.certificates[2] as X509Certificate
        if (!root.subjectDN.name.contains(SGX_ROOT_CA_CN_PHRASE))
            return SGX_ROOT_CA_MISSING

        val intermed = pckCertChain.certificates[1] as X509Certificate
        if (!intermed.subjectDN.name.contains(SGX_INTERMEDIATE_CN_PHRASE))
            return SGX_INTERMEDIATE_CA_MISSING

        val pck = pckCertChain.certificates[0] as X509Certificate
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

        val checkRootCaCrlCorrectness = verifyWithCrl(root, rootCaCrl)
        if (checkRootCaCrlCorrectness != OK)
            return checkRootCaCrlCorrectness

        val checkIntermediateCrlCorrectness = verifyWithCrl(intermed, intermediateCaCrl)
        if (checkIntermediateCrlCorrectness != OK)
            return checkIntermediateCrlCorrectness

        if (rootCaCrl.isRevoked(intermed))
            return SGX_INTERMEDIATE_CA_REVOKED

        if (intermediateCaCrl.isRevoked(pck))
            return SGX_PCK_REVOKED

        try {
            val dt = Date.from(currentTime)
            root.checkValidity(dt)
            intermed.checkValidity(dt)
            pck.checkValidity(dt)
        } catch (ex: CertificateException) {
            return SGX_PCK_CERT_CHAIN_EXPIRED
        }

        if (currentTime.isAfter(rootCaCrl.nextUpdate.toInstant()))
            return SGX_CRL_EXPIRED

        if (currentTime.isAfter(intermediateCaCrl.nextUpdate.toInstant()))
            return SGX_CRL_EXPIRED

        return OK
    }

    /// QuoteVerification/QVL/Src/AttestationLibrary/src/QuoteVerification.cpp:64 - parsing inputs
    /// QuoteVerification/QVL/Src/AttestationLibrary/src/Verifiers/TCBInfoVerifier.cpp:59
    /// QuoteVerification/QVL/Src/AttestationLibrary/src/Verifiers/TCBSigningChain.cpp:51
    private fun verifyTCBInfo(tcbInfo: TcbInfoSigned, tcbSignChain: CertPath, rootCaCrl: X509CRL, trustedRootCaCert: X509Certificate, currentTime: Instant): Status {
        val tcbChainVerificationResult: Status = verifyTcbChain(tcbSignChain, rootCaCrl, trustedRootCaCert)
        if (tcbChainVerificationResult != OK)
            return tcbChainVerificationResult

        val tcbSigningCert = tcbSignChain.certificates[0] as X509Certificate
        val rootCert = tcbSignChain.certificates[1] as X509Certificate

        val tcbInfoSignatureValidationStatus = validateTcbSignature(tcbInfo, tcbSigningCert)
        if (tcbInfoSignatureValidationStatus != OK)
            return tcbInfoSignatureValidationStatus

        try {
            val dt = Date.from(currentTime)
            rootCert.checkValidity(dt)
            tcbSigningCert.checkValidity(dt)
        } catch (ex: CertificateException) {
            return SGX_SIGNING_CERT_CHAIN_EXPIRED
        }
        if (currentTime.isAfter(rootCaCrl.nextUpdate.toInstant()))
            return SGX_CRL_EXPIRED

        if (currentTime.isAfter(tcbInfo.tcbInfo.nextUpdate.toInstant())) {
            return SGX_TCB_INFO_EXPIRED
        }

        return OK
    }

    /// QuoteVerification/QVL/Src/AttestationLibrary/src/QuoteVerification.cpp:232
    /// QuoteVerification/QVL/Src/AttestationLibrary/src/Verifiers/EnclaveIdentityVerifier.cpp:60
    private fun verifyQeIdentity(qeIdentity: EnclaveIdentitySigned, qeIdentityIssuerChain: CertPath, rootCaCrl: X509CRL, trustedRootCaCert: X509Certificate, currentTime: Instant): Status {
        // yes, verifyTcbChain is used to verify qeIdentityIssuerChain
        val qeIdentityIssuerChainVerificationResult = verifyTcbChain(qeIdentityIssuerChain, rootCaCrl, trustedRootCaCert)
        if (qeIdentityIssuerChainVerificationResult != OK)
            return qeIdentityIssuerChainVerificationResult

        val signingCert = qeIdentityIssuerChain.certificates[0] as X509Certificate
        val rootCert = qeIdentityIssuerChain.certificates[1] as X509Certificate
        val tcbInfoSignatureValidationStatus = validateEnclaveIdentitySignature(qeIdentity, signingCert)
        if (tcbInfoSignatureValidationStatus != OK)
            return tcbInfoSignatureValidationStatus

        try {
            val dt = Date.from(currentTime)
            rootCert.checkValidity(dt)
            signingCert.checkValidity(dt)
        } catch (ex: CertificateException) {
            return SGX_SIGNING_CERT_CHAIN_EXPIRED
        }

        if (currentTime.isAfter(rootCaCrl.nextUpdate.toInstant()))
            return SGX_CRL_EXPIRED

        if (currentTime.isAfter(qeIdentity.enclaveIdentity.nextUpdate.toInstant()))
            return SGX_ENCLAVE_IDENTITY_EXPIRED

        return OK
    }

    private fun validateEnclaveIdentitySignature(identity: EnclaveIdentitySigned, tcbSigningCert: Certificate): Status {
        val objectMapper = ObjectMapper()
        val body = objectMapper.writeValueAsBytes(identity.enclaveIdentity)
        val signature = derEncodedSignature(identity.signature)
        val verifier = Signature.getInstance("SHA256withECDSA")
        verifier.initVerify(tcbSigningCert)
        verifier.update(body)
        if (!verifier.verify(signature))
            return SGX_ENCLAVE_IDENTITY_INVALID_SIGNATURE

        return OK
    }

    private fun validateTcbSignature(tcbInfo: TcbInfoSigned, tcbSigningCert: X509Certificate): Status {
        val objectMapper = ObjectMapper()
        val body = objectMapper.writeValueAsBytes(tcbInfo.tcbInfo)
        val signature = derEncodedSignature(tcbInfo.signature)
        val verifier = Signature.getInstance("SHA256withECDSA")
        verifier.initVerify(tcbSigningCert)
        verifier.update(body)
        if (!verifier.verify(signature))
            return TCB_INFO_INVALID_SIGNATURE

        return OK
    }

    private fun derEncodedSignature(hex: String): ByteArray {
        return derEncodedSignature(parseHex(hex), 0)
    }

    private fun derEncodedSignature(data: ByteArray, offset: Int): ByteArray {
        // Java 11
        //val b1 = BigInteger(1, data, offset, 32)
        //val b2 = BigInteger(1, data, offset + 32, 32)
        // Java 8
        val b1 = BigInteger(1, data.copyOfRange(offset, offset + 32))
        val b2 = BigInteger(1, data.copyOfRange(offset + 32, offset + 64))
        val data1 = b1.toByteArray()
        val data2 = b2.toByteArray()
        val output = ByteArray(6 + data1.size + data2.size)
        output[0] = 0x30
        output[1] = (4 + data1.size + data2.size).toByte()
        encodeChunk(output, 2, data1)
        encodeChunk(output, 4 + data1.size, data2)
        return output
    }

    private fun encodeChunk(output: ByteArray, offset: Int, chunk: ByteArray) {
        output[offset] = 0x02
        output[offset + 1] = chunk.size.toByte()
        System.arraycopy(chunk, 0, output, offset + 2, chunk.size)
    }

    /// QuoteVerification/QVL/Src/AttestationLibrary/src/Verifiers/TCBSigningChain.cpp:56
    private fun verifyTcbChain(tcbSignChain: CertPath, rootCaCrl: X509CRL, trustedRootCaCert: X509Certificate): Status {
        if (tcbSignChain.certificates.size != 2)
            return UNSUPPORTED_CERT_FORMAT

        val rootCert = tcbSignChain.certificates[1] as X509Certificate

        if (!rootCert.subjectDN.name.contains(SGX_ROOT_CA_CN_PHRASE))
            return SGX_ROOT_CA_MISSING

        if (!verifyWithIssuer(rootCert, rootCert))
            return SGX_ROOT_CA_INVALID_ISSUER

        if (!verifyWithIssuer(rootCert, trustedRootCaCert))
            return SGX_ROOT_CA_INVALID_ISSUER

        val tcbSigningCert = tcbSignChain.certificates[0] as X509Certificate
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

    private fun checkAttestationKeyAndQeReportDataHash(ecdsa: ECDSASignature): Boolean {
        val pubKey: ByteArray = ecdsa.getPublicKeyRaw()
        val authData: ByteArray = ecdsa.getAuthDataRaw()

        val qeReport = ecdsa.getQEReport()
        val qeReportBody = Cursor.wrap(SgxReportBody, qeReport, 0, qeReport.size)

        val pkAuth = ByteArray(pubKey.size + authData.size)
        pubKey.copyInto(pkAuth, 0, 0, pubKey.size)
        authData.copyInto(pkAuth, pubKey.size, 0, authData.size)
        val hash = SHA256Hash.hash(pkAuth).bytes

        // hash is 32 bytes, and report data is 64 bytes
        return Arrays.equals(hash.copyOf(64), qeReportBody[SgxReportBody.reportData].bytes)
    }

    /// QuoteVerification/QVL/Src/AttestationLibrary/src/Verifiers/EnclaveReportVerifier.cpp:47
    private fun verifyEnclaveReport(enclaveReport: ByteArray, enclaveIdentity: EnclaveIdentity): Status {
        // enclave report vs enclave identify json
        // (only used with QE and QE Identity, actually)

        /// 4.1.2.9.5
        val enclaveReportBody = Cursor.wrap(SgxReportBody, enclaveReport, 0, enclaveReport.size)
        val miscselectMask = getInt32(parseHex(enclaveIdentity.miscselectMask).reversedArray(), 0)
        val miscselect = getInt32(parseHex(enclaveIdentity.miscselect).reversedArray(), 0)
        if ((enclaveReportBody[SgxReportBody.miscSelect].read() and miscselectMask) != miscselect) {
            return SGX_ENCLAVE_REPORT_MISCSELECT_MISMATCH
        }

        /// 4.1.2.9.6
        val attributes = parseHex(enclaveIdentity.attributes)
        val attributesMask = parseHex(enclaveIdentity.attributesMask)
        val reportAttributes = enclaveReportBody[SgxReportBody.attributes].bytes
        for (i in 0..7) {
            if ((reportAttributes[i].toInt() and attributesMask[i].toInt()) != attributes[i].toInt())
                return SGX_ENCLAVE_REPORT_ATTRIBUTES_MISMATCH
        }

        /// 4.1.2.9.8
        val mrsigner = enclaveIdentity.mrsigner
        if (!mrsigner.isNullOrEmpty() && !mrsigner.equals(enclaveReportBody[SgxReportBody.mrsigner].bytes.toHexString(), ignoreCase = true)) {
            return SGX_ENCLAVE_REPORT_MRSIGNER_MISMATCH
        }

        /// 4.1.2.9.9
        if (enclaveReportBody[SgxReportBody.isvProdId].read() != enclaveIdentity.isvprodid) {
            return SGX_ENCLAVE_REPORT_ISVPRODID_MISMATCH
        }

        /// 4.1.2.9.10 & 4.1.2.9.11
        val isvSvn = enclaveReportBody[SgxReportBody.isvSvn].read()
        val tcbStatus = getTcbStatus(isvSvn, enclaveIdentity.tcbLevels)
        if (tcbStatus != "UpToDate") {
            return if (tcbStatus == "Revoked") SGX_ENCLAVE_REPORT_ISVSVN_REVOKED
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

    private fun getTcbStatus(isvSvn: Int, levels: List<TcbLevelShort>): String {
        for (lvl in levels) {
            if (lvl.tcb.isvsvn <= isvSvn)
                return lvl.tcbStatus
        }
        return "Revoked"
    }


    private fun getMatchingTcbLevel(tcbInfo: TcbInfo, pckTcb: IntArray, pckPceSvn: Int): String {
        for (lvl in tcbInfo.tcbLevels) {
            if (isCpuSvnHigherOrEqual(pckTcb, lvl.tcb) && pckPceSvn >= (lvl.tcb.pcesvn as Int)) {
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
            tcbLevelStatus == "OutOfDate" -> TCB_OUT_OF_DATE
            tcbLevelStatus == "Revoked" -> TCB_REVOKED
            tcbLevelStatus == "ConfigurationNeeded" -> TCB_CONFIGURATION_NEEDED
            tcbLevelStatus == "ConfigurationAndSWHardeningNeeded" -> TCB_CONFIGURATION_AND_SW_HARDENING_NEEDED
            tcbLevelStatus == "UpToDate" -> OK
            tcbLevelStatus == "SWHardeningNeeded" -> TCB_SW_HARDENING_NEEDED
            tcbInfo.version == 2 && tcbLevelStatus == "OutOfDateConfigurationNeeded" -> TCB_OUT_OF_DATE_CONFIGURATION_NEEDED
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
        } as Int
    }

    private fun getFmspcAndPceIdAndTcbs(pckCert: X509Certificate, tcbs: IntArray): Array<ByteArray> {
        val decoder = SGXExtensionASN1Parser()
        val ext = pckCert.getExtensionValue(SGX_EXTENSION_OID)
        decoder.parse(ext, ext.size)

        for (i in 1..16)
            tcbs[i - 1] = (decoder.intValue("$SGX_EXTENSION_TCB.$i"))

        return arrayOf(decoder.value(SGX_EXTENSION_FMSPC_OID), decoder.value(SGX_EXTENSION_PCEID_OID), decoder.value(SGX_EXTENSION_PCESVN))
    }

    private fun getInt16(data: ByteArray, offset: Int): Long {
        return (data[offset] + (data[offset + 1] * 256)).toLong() and 0x0000FFFF
    }

    private fun getInt32(data: ByteArray, offset: Int): Long {
        return (getInt16(data, offset) + getInt16(data, offset + 2) * 256 * 256) and 0x0FFFFFFFF
    }

    private fun parseCRL(pem: String): X509CRL {
        return CertificateFactory.getInstance("X.509").generateCRL(pem.byteInputStream()) as X509CRL
    }

    private fun parseCertPath(pem: String): CertPath {
        val certificateFactory = CertificateFactory.getInstance("X.509")
        val certificates = mutableListOf<Certificate>()

        val input = ByteArrayInputStream(pem.toByteArray())
        while (input.available() > 1) {
            certificates += certificateFactory.generateCertificate(input)
        }

        return certificateFactory.generateCertPath(certificates)
    }

    private fun getLatestIssueDate(identity: EnclaveIdentity, latestIssueDate: Instant): Instant {
        val issued = identity.issueDate.toInstant()
        return if (issued.isAfter(latestIssueDate)) issued else latestIssueDate
    }

    private fun getEarliestExpirationDate(identity: EnclaveIdentity, earliestExpireDate: Instant): Instant {
        val expire = identity.nextUpdate.toInstant()
        return if (expire.isBefore(earliestExpireDate)) expire else earliestExpireDate
    }

    private fun getLatestIssueDate(tcb: TcbInfo, latestIssueDate: Instant): Instant {
        val issued = tcb.issueDate.toInstant()
        return if (issued.isAfter(latestIssueDate)) issued else latestIssueDate
    }

    private fun getEarliestExpirationDate(tcb: TcbInfo, earliestExpireDate: Instant): Instant {
        val expire = tcb.nextUpdate.toInstant()
        return if (expire.isBefore(earliestExpireDate)) expire else earliestExpireDate
    }

    private fun getLatestIssueDate(crl: X509CRL, latestIssueDate: Instant): Instant {
        val issued = crl.thisUpdate.toInstant()
        return if (issued.isAfter(latestIssueDate)) issued else latestIssueDate
    }

    private fun getEarliestExpirationDate(crl: X509CRL, earliestExpireDate: Instant): Instant {
        val expire = crl.nextUpdate.toInstant()
        return if (expire.isBefore(earliestExpireDate)) expire else earliestExpireDate
    }

    private fun getLatestIssueDate(cert: X509Certificate, latestIssueDate: Instant): Instant {
        val issued = cert.notBefore.toInstant()
        return if (issued.isAfter(latestIssueDate)) issued else latestIssueDate
    }

    private fun getLatestIssueDate(chain: CertPath, latestIssueDate: Instant): Instant {
        var latestIssueDateOut = latestIssueDate
        for (cert in chain.certificates) latestIssueDateOut = getLatestIssueDate(cert as X509Certificate, latestIssueDateOut)
        return latestIssueDateOut
    }

    private fun getEarliestExpirationDate(cert: X509Certificate, earliestExpireDate: Instant): Instant {
        val expire = cert.notAfter.toInstant()
        return if (expire.isBefore(earliestExpireDate)) expire else earliestExpireDate
    }

    private fun getEarliestExpirationDate(certPath: CertPath, earliestExpireDate: Instant): Instant {
        var earliestExpireDateOut = earliestExpireDate
        for (cert in certPath.certificates) earliestExpireDateOut = getEarliestExpirationDate(cert as X509Certificate, earliestExpireDateOut)
        return earliestExpireDateOut
    }

}
