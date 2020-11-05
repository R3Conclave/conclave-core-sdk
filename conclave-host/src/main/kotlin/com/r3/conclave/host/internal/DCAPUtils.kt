package com.r3.conclave.host.internal

import com.r3.conclave.common.internal.SGXExtensionASN1Parser
import java.security.cert.CertPath
import java.security.cert.Certificate
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate

class DCAPUtils {
    companion object {
        private const val SGX_EXTENSION_OID = "1.2.840.113741.1.13.1"
        private const val SGX_FMSPC_OID = "1.2.840.113741.1.13.1.4"

        private const val withDelimeterRegex = "(?<=%1\$s)"
        private const val endCertificateMarker = "-----END CERTIFICATE-----"
        private const val endCertificateMarkerLength = endCertificateMarker.length
        private val endCertificateRegex = Regex(String.format(withDelimeterRegex, endCertificateMarker))

        fun parsePemCertPathFromSignature(data: ByteArray): CertPath {
            return parsePemCertPath(data, 0)
        }

        private fun parsePemCertPath(data: ByteArray, base: Int): CertPath {
            // ecdsa signature data
            // sig      +0      64
            // pub_key  +64     64
            // rep_body +128    384
            // rep_sig  +512    64
            // auth_cert_data   +576
            var offset = base + 576

            // auth data
            // size +0  2
            // data +2  size
            val authDataSize = getInt16(data, offset)
            offset += 2 + authDataSize

            // cert data
            // key  +0  2
            // size +2  4
            // data +6  size
            //val key_type = getInt16(data, offset)
            offset += 2
            //val cert_data_size = getInt32(data, offset)
            offset += 4

            // plaintext certs start here
            return parseCertChain(String(data, offset, data.size - offset))
        }

        fun parseCertChain(data: String): CertPath {
            val certificateFactory = CertificateFactory.getInstance("X.509")
            val certificates = mutableListOf<Certificate>()

            for (text in data.split(endCertificateRegex)) {
                if (text.length < endCertificateMarkerLength)
                    break;

                certificates.add(certificateFactory.generateCertificate(text.byteInputStream()))
            }
            return certificateFactory.generateCertPath(certificates)
        }

        private fun getInt16(data: ByteArray, offset: Int): Int {
            return data[offset] + data[offset + 1] * 256
        }

        fun getSgxExtension(certPath: CertPath): ByteArray {
            val topCert = certPath.certificates[0] as X509Certificate
            return topCert.getExtensionValue(SGX_EXTENSION_OID)
        }

        fun getFMSPC(ext: ByteArray): ByteArray {
            val parser = SGXExtensionASN1Parser()
            parser.parse(ext, ext.size)
            return parser.value(SGX_FMSPC_OID)
        }

        fun getPckWord(certPath: CertPath): Int {
            val topCert = certPath.certificates[0] as X509Certificate
            return if ("Processor" in topCert.issuerDN.name) 0 else 1
        }

        fun getFMSPC(certPath: CertPath): ByteArray {
            return getFMSPC(getSgxExtension(certPath))
        }
    }
}
