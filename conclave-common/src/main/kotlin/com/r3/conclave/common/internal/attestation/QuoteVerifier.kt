package com.r3.conclave.common.internal.attestation

import com.r3.conclave.common.SHA256Hash
import com.r3.conclave.common.internal.Cursor
import com.r3.conclave.common.internal.ECDSASignature
import com.r3.conclave.common.internal.SGXExtensionASN1Parser
import com.r3.conclave.common.internal.SgxReportBody
import com.r3.conclave.common.internal.attestation.QuoteVerifier.Status.*
import com.r3.conclave.utilities.internal.parseHex
import com.r3.conclave.utilities.internal.toHexString
import java.io.StringReader
import java.security.GeneralSecurityException
import java.security.Signature
import java.security.cert.*

// original https://github.com/intel/SGXDataCenterAttestationPrimitives/blob/master/QuoteVerification/QVL/Src/AttestationLibrary/src/Verifiers/QuoteVerifier.cpp
object QuoteVerifier {

    enum class Status {
        STATUS_OK,
        STATUS_SGX_ENCLAVE_REPORT_ISVSVN_OUT_OF_DATE,
        STATUS_SGX_ENCLAVE_REPORT_MISCSELECT_MISMATCH,
        STATUS_SGX_ENCLAVE_REPORT_ATTRIBUTES_MISMATCH,
        STATUS_SGX_ENCLAVE_REPORT_MRSIGNER_MISMATCH,
        STATUS_SGX_ENCLAVE_REPORT_ISVPRODID_MISMATCH,
        STATUS_SGX_ENCLAVE_REPORT_ISVSVN_REVOKED,
        STATUS_INVALID_PCK_CERT,
        STATUS_PCK_REVOKED,
        STATUS_INVALID_PCK_CRL,
        STATUS_TCB_INFO_MISMATCH,
        STATUS_TCB_OUT_OF_DATE_CONFIGURATION_NEEDED,
        STATUS_TCB_OUT_OF_DATE,
        STATUS_TCB_REVOKED,
        STATUS_TCB_SW_HARDENING_NEEDED,
        STATUS_TCB_CONFIGURATION_NEEDED,
        STATUS_TCB_CONFIGURATION_AND_SW_HARDENING_NEEDED,
        STATUS_TCB_UNRECOGNIZED_STATUS
    }

    private const val SGX_EXTENSION_OID = "1.2.840.113741.1.13.1"
    private const val SGX_EXTENSION_FMSPC_OID = "1.2.840.113741.1.13.1.4"
    private const val SGX_EXTENSION_PCEID_OID = "1.2.840.113741.1.13.1.3"
    private const val SGX_EXTENSION_TCB = "1.2.840.113741.1.13.1.2"
    private const val SGX_EXTENSION_PCESVN = "1.2.840.113741.1.13.1.2.17"
    /// SGX_EXTENSION_PCEID_TCB_LEVEL_N = "1.2.840.113741.1.13.1.2.[1..16]"

    private const val QUOTE_VERSION = 3
    private const val QUOTE_KEY_TYPE = 2

    private const val SGX_PCK_CN_PHRASE = "SGX PCK Certificate"
    private const val SGX_INTERMEDIATE_CN_PHRASE = "CA"

    fun verify(reportBytes: ByteArray, signature: ByteArray, certPath: CertPath, collateral: QuoteCollateral) {
        quote_version_check(reportBytes)
        val isvStatus = cert_collateral_check(certPath, collateral)

        val ecdsa = ECDSASignature(signature)

        signature_check(ecdsa, reportBytes, certPath)
        pubkey_authdata_hash_check(ecdsa)

        val qeStatus = qe_identity_check(ecdsa, collateral)

        when (val status = convergeTcbStatus(isvStatus, qeStatus)) {
            STATUS_OK,
            STATUS_TCB_SW_HARDENING_NEEDED,
            STATUS_TCB_CONFIGURATION_AND_SW_HARDENING_NEEDED,
            STATUS_TCB_CONFIGURATION_NEEDED,
            STATUS_TCB_OUT_OF_DATE,
            STATUS_TCB_OUT_OF_DATE_CONFIGURATION_NEEDED -> {}
            else -> throw GeneralSecurityException("QuoteVerify $status")
            /*
            7. SGX_QL_QV_RESULT_INVALID_SIGNATURE – Terminal
            8. SGX_QL_QV_RESULT_REVOKED – Terminal
            9. SGX_QL_QV_RESULT_UNSPECIFIED – Terminal
             */
        }
    }

    private fun quote_version_check(reportBytes: ByteArray) {
        val version = getQuoteVersion(reportBytes)
        if (version != QUOTE_VERSION)
            throw GeneralSecurityException("Unsupported quote version $version")

        val attKeyType = getQuoteAttestationKeyType(reportBytes)
        if (attKeyType != QUOTE_KEY_TYPE)
            throw GeneralSecurityException("Unsupported quote key type $attKeyType")
    }

    internal fun cert_collateral_check(certPath: CertPath, collateral: QuoteCollateral): Status {
        /// 4.1.2.4.4
        val pckCert = certPath.certificates[0] as X509Certificate // from quote
        //println(pckCert)
        if (SGX_PCK_CN_PHRASE !in pckCert.subjectDN.name)
            return STATUS_INVALID_PCK_CERT

        /// 4.1.2.4.6
        val pckCrl = parseCRL(collateral.pckCrl)
        if (SGX_INTERMEDIATE_CN_PHRASE !in pckCrl.issuerDN.name)
            return STATUS_INVALID_PCK_CRL

        /// 4.1.2.4.6
        if (pckCrl.issuerDN != pckCert.issuerDN)
            return STATUS_INVALID_PCK_CRL

        /// 4.1.2.4.7
        if (pckCrl.isRevoked(pckCert))
            return STATUS_PCK_REVOKED

        val pckCertTCBs = IntArray(16)
        val (fmspc, pceid, pcesvn) = get_fmspc_pceid_tcb(pckCert, pckCertTCBs)

        val tcbSigned = attestationObjectMapper.readValue(StringReader(collateral.tcbInfo), TcbInfoSigned::class.java)

        /// 4.1.2.4.9
        if (!fmspc.toHexString().equals(tcbSigned.tcbInfo?.fmspc, ignoreCase = true))
            return STATUS_TCB_INFO_MISMATCH

        if (!pceid.toHexString().equals(tcbSigned.tcbInfo?.pceId, ignoreCase = true)) {
            return STATUS_TCB_INFO_MISMATCH
        }

        return tcb_status_check(pckCertTCBs, tcbSigned.tcbInfo as TcbInfo, SGXExtensionASN1Parser.intValue(pcesvn))
    }

    private fun signature_check(ecdsa: ECDSASignature, reportBytes: ByteArray, certPath: CertPath) {
        // 'main' enclave
        val subjectSignature = ecdsa.getSignature()

        val signatureAlgo = "SHA256withECDSA"
        Signature.getInstance(signatureAlgo).apply {
            initVerify(ecdsa.getPublicKey())
            update(reportBytes)
            if (!verify(subjectSignature)) {
                throw GeneralSecurityException("ISV Enclave: Attestation report failed signature check")
            }
        }

        // quoting enclave
        val qeReport = ecdsa.getQEReport()
        val qeSignature = ecdsa.getQESignature()
        Signature.getInstance(signatureAlgo).apply {
            initVerify(certPath.certificates[0])
            update(qeReport)
            if (!verify(qeSignature)) {
                throw GeneralSecurityException("Quoting Enclave: Attestation report failed signature check")
            }
        }
    }

    private fun pubkey_authdata_hash_check(ecdsa: ECDSASignature) {
        val pubKey: ByteArray = ecdsa.getPublicKeyRaw()
        val authData: ByteArray = ecdsa.getAuthDataRaw()

        val qeReport = ecdsa.getQEReport()
        val qeReportBody = Cursor.wrap(SgxReportBody, qeReport, 0, qeReport.size)

        val pkAuth = ByteArray(pubKey.size + authData.size)
        pubKey.copyInto(pkAuth, 0, 0, pubKey.size)
        authData.copyInto(pkAuth, pubKey.size, 0, authData.size)
        val hash = SHA256Hash.hash(pkAuth).bytes

        // hash is 32bytes, and report data is 64 bytes
        assert_partial_equality(hash, qeReportBody[SgxReportBody.reportData].bytes)
    }

    private fun qe_identity_check(ecdsa: ECDSASignature, collateral: QuoteCollateral): Status {
        // qe identity verification - qe report vs qe_identify json
        val qeIdentity = attestationObjectMapper.readValue(collateral.qeIdentity, EnclaveIdentitySigned::class.java)

        /// 4.1.2.9.5
        val qeReport = ecdsa.getQEReport()
        val qeReportBody = Cursor.wrap(SgxReportBody, qeReport, 0, qeReport.size)
        val miscselectMask = getInt32(parseHex(qeIdentity.enclaveIdentity?.miscselectMask!!).reversedArray(), 0)
        val miscselect = getInt32(parseHex(qeIdentity.enclaveIdentity.miscselect!!).reversedArray(), 0)
        if ((qeReportBody[SgxReportBody.miscSelect].read() and miscselectMask) != miscselect) {
            return STATUS_SGX_ENCLAVE_REPORT_MISCSELECT_MISMATCH
        }

        /// 4.1.2.9.6
        val attributes = parseHex(qeIdentity.enclaveIdentity.attributes!!)
        val attributesMask = parseHex(qeIdentity.enclaveIdentity.attributesMask!!)
        val reportAttributes = qeReportBody[SgxReportBody.attributes].bytes
        for (i in 0..7) {
            if ((reportAttributes[i].toInt() and attributesMask[i].toInt()) != attributes[i].toInt())
                return STATUS_SGX_ENCLAVE_REPORT_ATTRIBUTES_MISMATCH
        }

        /// 4.1.2.9.8
        val mrsigner = qeIdentity.enclaveIdentity.mrsigner
        if (!mrsigner.isNullOrEmpty() && !mrsigner.equals(qeReportBody[SgxReportBody.mrsigner].bytes.toHexString(), ignoreCase = true)) {
            return STATUS_SGX_ENCLAVE_REPORT_MRSIGNER_MISMATCH
        }

        /// 4.1.2.9.9
        if (qeReportBody[SgxReportBody.isvProdId].read() != qeIdentity.enclaveIdentity.isvprodid) {
            return STATUS_SGX_ENCLAVE_REPORT_ISVPRODID_MISMATCH
        }

        val isvSvn = qeReportBody[SgxReportBody.isvSvn].read()
        val tcbStatus = get_tcb_status(isvSvn, qeIdentity.enclaveIdentity.tcbLevels!!)
        if (tcbStatus != "UpToDate") {
            return if (tcbStatus == "Revoked") STATUS_SGX_ENCLAVE_REPORT_ISVSVN_REVOKED else STATUS_SGX_ENCLAVE_REPORT_ISVSVN_OUT_OF_DATE
        }

        return STATUS_OK
    }

    private fun convergeTcbStatus(tcbLevelStatus: Status, qeTcbStatus: Status): Status? {
        if (qeTcbStatus === STATUS_SGX_ENCLAVE_REPORT_ISVSVN_OUT_OF_DATE) {
            if (tcbLevelStatus === STATUS_OK ||
                    tcbLevelStatus === STATUS_TCB_SW_HARDENING_NEEDED) {
                return STATUS_TCB_OUT_OF_DATE
            }
            if (tcbLevelStatus === STATUS_TCB_CONFIGURATION_NEEDED ||
                    tcbLevelStatus === STATUS_TCB_CONFIGURATION_AND_SW_HARDENING_NEEDED) {
                return STATUS_TCB_OUT_OF_DATE_CONFIGURATION_NEEDED
            }
        }

        if (qeTcbStatus === STATUS_SGX_ENCLAVE_REPORT_ISVSVN_REVOKED) {
            return STATUS_TCB_REVOKED
        }

        return when (tcbLevelStatus) {
            STATUS_TCB_OUT_OF_DATE,
            STATUS_TCB_REVOKED,
            STATUS_TCB_CONFIGURATION_NEEDED,
            STATUS_TCB_OUT_OF_DATE_CONFIGURATION_NEEDED,
            STATUS_TCB_SW_HARDENING_NEEDED,
            STATUS_TCB_CONFIGURATION_AND_SW_HARDENING_NEEDED,
            STATUS_OK -> tcbLevelStatus
            else -> STATUS_TCB_UNRECOGNIZED_STATUS
        }
    }

    private fun get_tcb_status(isvSvn: Int, levels: List<TcbLevelShort>): String {
        for (lvl in levels) {
            if (lvl.tcb?.isvsvn!! <= isvSvn)
                return lvl.tcbStatus!!
        }
        return "Revoked"
    }


    private fun getMatchingTcbLevel(tcbInfo: TcbInfo, pckTcb: IntArray, pckPceSvn: Int): String {
        for (lvl in (tcbInfo.tcbLevels as List)) {
            if (isCpuSvnHigherOrEqual(pckTcb, lvl.tcb as Tcb) && pckPceSvn >= (lvl.tcb.pcesvn as Int)) {
                return lvl.tcbStatus!!
            }
        }

        throw GeneralSecurityException("STATUS_TCB_NOT_SUPPORTED")
    }

    private fun isCpuSvnHigherOrEqual(pckTcb: IntArray, jsonTcb: Tcb): Boolean {
        for (j in pckTcb.indices) {
            // If *ANY* CPUSVN component is lower then CPUSVN is considered lower
            if (pckTcb[j] < getSgxTcbComponentSvn(jsonTcb, j)) return false
        }
        // but for CPUSVN to be considered higher it requires that *EVERY* CPUSVN component to be higher or equal
        return true
    }

    private fun tcb_status_check(pckCertTCBs: IntArray, tcbInfo: TcbInfo, pcesvn: Int): Status {
        /// 4.1.2.4.17.1 & 4.1.2.4.17.2
        val tcbLevelStatus = getMatchingTcbLevel(tcbInfo, pckCertTCBs, pcesvn)

        return when {
            tcbLevelStatus == "OutOfDate" -> STATUS_TCB_OUT_OF_DATE
            tcbLevelStatus == "Revoked" -> STATUS_TCB_REVOKED
            tcbLevelStatus == "ConfigurationNeeded" -> STATUS_TCB_CONFIGURATION_NEEDED
            tcbLevelStatus == "ConfigurationAndSWHardeningNeeded" -> STATUS_TCB_CONFIGURATION_AND_SW_HARDENING_NEEDED
            tcbLevelStatus == "UpToDate" -> STATUS_OK
            tcbLevelStatus == "SWHardeningNeeded" -> STATUS_TCB_SW_HARDENING_NEEDED
            tcbInfo.version == 2 && tcbLevelStatus == "OutOfDateConfigurationNeeded" -> STATUS_TCB_OUT_OF_DATE_CONFIGURATION_NEEDED
            else -> throw GeneralSecurityException("STATUS_TCB_UNRECOGNIZED_STATUS")
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

    private fun assert_partial_equality(a: ByteArray, b: ByteArray) {
        var i = 0
        while (i < a.size && i < b.size) {
            if (a[i] != b[i]) {
                throw GeneralSecurityException("""STATUS_INVALID_QE_REPORT_DATA
A=${a.toHexString()}
B=${b.toHexString()}""")
            }
            i++
        }
    }

    private fun get_fmspc_pceid_tcb(pckCert: X509Certificate, tcbs: IntArray): Array<ByteArray> {
        val decoder = SGXExtensionASN1Parser()
        val ext = pckCert.getExtensionValue(SGX_EXTENSION_OID)
        decoder.parse(ext, ext.size)

        for (i in 1..16)
            tcbs[i - 1] = (decoder.intValue("$SGX_EXTENSION_TCB.$i"))

        return arrayOf(decoder.value(SGX_EXTENSION_FMSPC_OID), decoder.value(SGX_EXTENSION_PCEID_OID), decoder.value(SGX_EXTENSION_PCESVN))
    }

    private fun getQuoteVersion(quote: ByteArray): Int {
        return getInt16(quote, 0).toInt()
    }

    private fun getQuoteAttestationKeyType(quote: ByteArray): Int {
        return getInt16(quote, 2).toInt()
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
}
