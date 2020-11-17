package com.r3.conclave.common.internal.attestation

import com.r3.conclave.common.internal.SGXExtensionASN1Parser
import com.r3.conclave.common.internal.inputStream
import com.r3.conclave.utilities.internal.getBytes
import com.r3.conclave.utilities.internal.getSlice
import com.r3.conclave.utilities.internal.x509Certs
import java.math.BigInteger
import java.nio.ByteBuffer
import java.nio.CharBuffer
import java.nio.charset.StandardCharsets
import java.security.cert.CertPath
import java.security.cert.Certificate
import java.security.cert.CertificateFactory
import java.util.regex.Pattern

object AttestationUtils {
    private const val SGX_EXTENSION_OID = "1.2.840.113741.1.13.1"
    private const val SGX_FMSPC_OID = "1.2.840.113741.1.13.1.4"

    private val PEM_CERT_PATTERN = Pattern.compile("-----BEGIN CERTIFICATE-----[^-]+-----END CERTIFICATE-----")

    private fun getSgxExtension(certPath: CertPath): ByteArray {
        return certPath.x509Certs[0].getExtensionValue(SGX_EXTENSION_OID)
    }

    fun getFMSPC(ext: ByteArray): ByteArray {
        val parser = SGXExtensionASN1Parser()
        parser.parse(ext, ext.size)
        return parser.value(SGX_FMSPC_OID)
    }

    fun getPckWord(certPath: CertPath): Int = if ("Processor" in certPath.x509Certs[0].issuerDN.name) 0 else 1

    fun getFMSPC(certPath: CertPath): ByteArray = getFMSPC(getSgxExtension(certPath))

    fun parseRawEcdsaToDerEncoding(buffer: ByteBuffer): ByteArray {
        // Java 11
        //val b1 = BigInteger(1, data, offset, 32)
        //val b2 = BigInteger(1, data, offset + 32, 32)
        // Java 8
        val data1 = BigInteger(1, buffer.getBytes(32)).toByteArray()
        val data2 = BigInteger(1, buffer.getBytes(32)).toByteArray()
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

    /**
     * Parse the given byte buffer representing a cert path encoded as concatented certificates in PEM format.
     */
    fun parsePemCertPath(bytes: ByteBuffer): CertPath {
        val chars: CharBuffer = StandardCharsets.US_ASCII.decode(bytes)

        val certificateFactory = CertificateFactory.getInstance("X.509")
        val certificates = mutableListOf<Certificate>()

        val matcher = PEM_CERT_PATTERN.matcher(chars)
        while (matcher.find()) {
            // PEM is encoded in ASCII so there's a one-to-one mapping between the byte and char indices.
            val size = matcher.end() - matcher.start()
            val certSlice = bytes.getSlice(size, matcher.start())
            certificates += certificateFactory.generateCertificate(certSlice.inputStream())
        }

        return certificateFactory.generateCertPath(certificates)
    }
}
