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
import com.r3.conclave.utilities.internal.rootX509Cert
import com.r3.conclave.utilities.internal.x509Certs
import java.nio.ByteBuffer
import java.security.GeneralSecurityException
import java.security.PublicKey
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
        INVALID_QUOTE_SIGNATURE,
        SGX_QL_PCK_CERT_CHAIN_ERROR,
        SGX_QL_COLLATERAL_VERSION_NOT_SUPPORTED
    }

    private const val QUOTE_VERSION = 3

    private const val INTEL_SUBJECT_PREFIX = "C=US, ST=CA, L=Santa Clara, O=Intel Corporation, "
    private val SGX_ROOT_CA_DN = X500Principal(INTEL_SUBJECT_PREFIX + "CN=Intel SGX Root CA")
    private val SGX_INTERMEDIATE_DN_PLATFORM = X500Principal(INTEL_SUBJECT_PREFIX + "CN=Intel SGX PCK Platform CA")
    private val SGX_INTERMEDIATE_DN_PROCESSOR = X500Principal(INTEL_SUBJECT_PREFIX + "CN=Intel SGX PCK Processor CA")
    private val SGX_PCK_DN = X500Principal(INTEL_SUBJECT_PREFIX + "CN=Intel SGX PCK Certificate")
    private val SGX_TCB_SIGNING_DN = X500Principal(INTEL_SUBJECT_PREFIX + "CN=Intel SGX TCB Signing")

    private val JSON_SIGNATURE_MARKER = """},"signature":"""".toByteArray()

    private val hardcodedIntelRootPublicKey: PublicKey
    private val collateralVersions: List<Int>

    init {
        //  QuoteVerification/QvE/Enclave/qve.cpp->line 76
        //  This is the Intel Root Public Key in ECDSA format.
        //    ECDSA uncompressed public keys have a size of  65 bytes, consisting of constant prefix (0x04, representing
        //    that the key is uncompressed) followed by two 256-bit integers, x and y (2 * 32 bytes).
        val hardcodedIntelPublicKeyBytes: ByteArray = intArrayOf(
            //  The first byte is a constant prefix, we are commenting it out as it is really not needed in our code.
            /*0x04,*/0x0b, 0xa9, 0xc4, 0xc0, 0xc0, 0xc8, 0x61, 0x93, 0xa3, 0xfe, 0x23, 0xd6, 0xb0, 0x2c,
            0xda, 0x10, 0xa8, 0xbb, 0xd4, 0xe8, 0x8e, 0x48, 0xb4, 0x45, 0x85, 0x61, 0xa3, 0x6e, 0x70,
            0x55, 0x25, 0xf5, 0x67, 0x91, 0x8e, 0x2e, 0xdc, 0x88, 0xe4, 0x0d, 0x86, 0x0b, 0xd0, 0xcc,
            0x4e, 0xe2, 0x6a, 0xac, 0xc9, 0x88, 0xe5, 0x05, 0xa9, 0x53, 0x55, 0x8c, 0x45, 0x3f, 0x6b,
            0x09, 0x04, 0xae, 0x73, 0x94
        ).map { it.toByte() }.toByteArray()
        hardcodedIntelRootPublicKey = Cursor.wrap(SgxEcdsa256BitPubkey, hardcodedIntelPublicKeyBytes).toPublicKey()

        //  QuoteVerification/QvE/Include/sgx_qve_def.h
        val qveCollateralVersion1 = 0x1
        val qveCollateralVersion3 = 0x3
        val qveCollateralVersion31 = 0x00010003
        val qveCollateralVersion4 = 0x4

        collateralVersions = listOf(qveCollateralVersion1, qveCollateralVersion3, qveCollateralVersion31, qveCollateralVersion4)
    }

    // QuoteVerification/QvE/Enclave/qve.cpp:sgx_qve_verify_quote
    fun verify(
        signedQuote: ByteCursor<SgxSignedQuote>,
        collateral: QuoteCollateral
    ): Pair<VerificationStatus, Instant> {
        val authData = signedQuote.toEcdsaP256AuthData()
        val pckCertPath = authData[qeCertData].toPckCertPath()

        val verificationStatus = try {
            verifyCollateralVersion(collateral.version)
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
        var latestIssueTime = pckCertPath.rootX509Cert.notBefore.toInstant()
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

    //  QuoteVerification/QvE/Enclave/qve.cpp:sgxAttestationVerifyQuote
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

    //  QuoteVerification/QvE/Enclave/qve.cpp:sgxAttestationVerifyPCKCertificate
    /// QuoteVerification/QVL/Src/AttestationLibrary/src/QuoteVerification.cpp:77 - parsing inputs
    /// QuoteVerification/QVL/Src/AttestationLibrary/src/Verifiers/PckCertVerifier.cpp:51 - verification
    private fun verifyPckCertificate(pckCertPath: CertPath, collateral: QuoteCollateral) {
        validateCertPath(pckCertPath)

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

    //  QuoteVerification/QvE/Enclave/qve.cpp->line 90
    //    if (hardcode_root_pub_key != root_pub_key_from_cert) {
    //      ret = SGX_QL_PCK_CERT_CHAIN_ERROR;
    //      break;
    //    }
    private fun validateRootPublicKey(rootCertificate: X509Certificate) {
        verify(hardcodedIntelRootPublicKey == rootCertificate.publicKey, SGX_QL_PCK_CERT_CHAIN_ERROR)
    }

    private fun verifyCollateralVersion(collateralVersion: Int) {
        verify(collateralVersion in collateralVersions, SGX_QL_COLLATERAL_VERSION_NOT_SUPPORTED)
    }

    //  QuoteVerification/QvE/Enclave/qve.cpp:sgxAttestationVerifyTCBInfo
    /// QuoteVerification/QVL/Src/AttestationLibrary/src/QuoteVerification.cpp:64 - parsing inputs
    /// QuoteVerification/QVL/Src/AttestationLibrary/src/Verifiers/TCBInfoVerifier.cpp:59
    /// QuoteVerification/QVL/Src/AttestationLibrary/src/Verifiers/TCBSigningChain.cpp:51
    private fun verifyTcbInfo(collateral: QuoteCollateral) {
        verifyTcbChain(collateral.tcbInfoIssuerChain, collateral.rootCaCrl)
        verifyJsonSignature(
            collateral.rawSignedTcbInfo,
            """{"tcbInfo":""",
            collateral.signedTcbInfo.signature,
            collateral.tcbInfoIssuerChain.x509Certs[0],
            TCB_INFO_INVALID_SIGNATURE
        )
    }

    //  QuoteVerification/QvE/Enclave/qve.cpp:sgxAttestationVerifyEnclaveIdentity
    /// QuoteVerification/QVL/Src/AttestationLibrary/src/QuoteVerification.cpp:232
    /// QuoteVerification/QVL/Src/AttestationLibrary/src/Verifiers/EnclaveIdentityVerifier.cpp:60
    private fun verifyQeIdentity(collateral: QuoteCollateral) {
        // yes, verifyTcbChain is used to verify qeIdentityIssuerChain
        verifyTcbChain(collateral.qeIdentityIssuerChain, collateral.rootCaCrl)
        verifyJsonSignature(
            collateral.rawSignedQeIdentity,
            """{"enclaveIdentity":""",
            collateral.signedQeIdentity.signature,
            collateral.qeIdentityIssuerChain.x509Certs[0],
            SGX_ENCLAVE_IDENTITY_INVALID_SIGNATURE
        )
    }

    private fun verifyJsonSignature(
        rawJson: OpaqueBytes,
        prefix: String,
        rawSignature: OpaqueBytes,
        cert: X509Certificate,
        errorStatus: ErrorStatus
    ) {
        // The documentation at https://api.portal.trustedservices.intel.com/documentation would have you believe that
        // simply removing the whitespace from the body is all that's needed to verify with the signature. However
        // JSON objects are *unordered* key/value pairs, and the encoding for any hex fields for this API accepts both
        // upper and lower case chars. So for these reasons we play it safe and verify over the body as it appears in the
        // raw JSON.
        val verifier = Signature.getInstance("SHA256withECDSA")
        verifier.initVerify(cert)
        val signedBytesLimit = rawJson.buffer().lastIndexOf(JSON_SIGNATURE_MARKER) + 1
        val signedBytesView = rawJson.buffer().setView(prefix.length, signedBytesLimit)
        verifier.update(signedBytesView)
        val signature = parseRawEcdsaToDerEncoding(rawSignature.buffer())
        verify(verifier.verify(signature), errorStatus)
    }

    /// QuoteVerification/QVL/Src/AttestationLibrary/src/Verifiers/TCBSigningChain.cpp:56
    private fun verifyTcbChain(tcbSignChain: CertPath, rootCaCrl: X509CRL) {
        validateCertPath(tcbSignChain)

        // Intel assumes a tcb chain of 2 certs
        verify(tcbSignChain.certificates.size == 2, UNSUPPORTED_CERT_FORMAT)

        val (tcbSigningCert, rootCert) = tcbSignChain.x509Certs
        verify(SGX_ROOT_CA_DN == rootCert.subjectX500Principal, SGX_ROOT_CA_MISSING)
        verify(SGX_TCB_SIGNING_DN == tcbSigningCert.subjectX500Principal, SGX_TCB_SIGNING_CERT_MISSING)

        verifyAgainstCrl(rootCert, rootCaCrl)
        verify(!rootCaCrl.isRevoked(tcbSigningCert), SGX_TCB_SIGNING_CERT_REVOKED)
    }


    private fun validateCertPath(chain: CertPath) {
        validateRootPublicKey(chain.rootX509Cert)

        val certTime = chain.x509Certs.minOf { it.notAfter }
        val pkixParameters = PKIXParameters(setOf(TrustAnchor(chain.rootX509Cert, null))).apply {
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

    private fun getMatchingTcbLevel(pckExtensions: SGXExtensionASN1Parser, tcbInfo: TcbInfo): TcbLevel {
        val pckTcbs = IntArray(16) { pckExtensions.getInt("${SGX_TCB_OID}.${it + 1}") }
        val pckPceSvn = pckExtensions.getInt(SGX_PCESVN_OID)

        for (tcbLevel in tcbInfo.tcbLevels) {
            if (isCpuSvnHigherOrEqual(pckTcbs, tcbLevel.tcb) && pckPceSvn >= tcbLevel.tcb.pcesvn) {
                if (tcbInfo.version.id >= 3 && tcbInfo.id == TypeID.TDX) {
                    /**
                     * Ignore TDX quotes for now
                     * TODO: CON-1273, Audit changes to the intel QVL
                     */
                    continue
                } else {
                    return tcbLevel
                }
            }
        }

        throw GeneralSecurityException("TCB_NOT_SUPPORTED")
    }

    private fun isCpuSvnHigherOrEqual(pckTcb: IntArray, jsonTcb: Tcb): Boolean {
        for (j in pckTcb.indices) {
            // If *ANY* CPUSVN component is lower then CPUSVN is considered lower
            if (pckTcb[j] < jsonTcb.sgxtcbcompsvn[j]) return false
        }
        // but for CPUSVN to be considered higher it requires that *EVERY* CPUSVN component to be higher or equal
        return true
    }

    private fun checkTcbLevel(pckExtensions: SGXExtensionASN1Parser, tcbInfo: TcbInfo): TcbStatus {
        val tcbLevel = getMatchingTcbLevel(pckExtensions, tcbInfo)

        check (!(tcbInfo.version.id > 1 && tcbLevel.tcbStatus == TcbStatus.OutOfDateConfigurationNeeded)) {
            "TCB_UNRCOGNISED_STATUS"
        }

        return tcbLevel.tcbStatus
    }

    private fun verify(check: Boolean, status: ErrorStatus) {
        if (!check) throw VerificationException(status)
    }

    private fun ByteBuffer.lastIndexOf(search: ByteArray): Int {
        for (index in limit() - search.size downTo position()) {
            if (containsBytesFromIndex(index, search)) {
                return index
            }
        }
        throw IllegalArgumentException()
    }

    private fun ByteBuffer.containsBytesFromIndex(startIndex: Int, search: ByteArray): Boolean {
        for (index in search.indices) {
            if (get(startIndex + index) != search[index]) {
                return false
            }
        }
        return true
    }

    private fun ByteBuffer.setView(start: Int, end: Int): ByteBuffer {
        position(start)
        limit(end)
        return this
    }

    private class VerificationException(val status: ErrorStatus, cause: Throwable? = null) : Exception(cause)
}

interface VerificationStatus
