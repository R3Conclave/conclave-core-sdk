package com.r3.conclave.common.internal.attestation

import com.r3.conclave.common.internal.SGXExtensionASN1Parser
import com.r3.conclave.common.internal.inputStream
import com.r3.conclave.utilities.internal.getBytes
import com.r3.conclave.utilities.internal.getSlice
import java.math.BigInteger
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import java.security.cert.CertPath
import java.security.cert.Certificate
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.util.regex.Pattern

object AttestationUtils {
    private const val SGX_EXTENSION_OID = "1.2.840.113741.1.13.1"

    const val SGX_TCB_OID = "$SGX_EXTENSION_OID.2"
    const val SGX_PCESVN_OID = "$SGX_TCB_OID.17"
    const val SGX_PCEID_OID = "$SGX_EXTENSION_OID.3"
    const val SGX_FMSPC_OID = "$SGX_EXTENSION_OID.4"

    private val PEM_CERT_PATTERN = Pattern.compile("-----BEGIN CERTIFICATE-----[^-]+-----END CERTIFICATE-----")

    val X509Certificate.sgxExtension: SGXExtensionASN1Parser
        get() = SGXExtensionASN1Parser(getExtensionValue(SGX_EXTENSION_OID))

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
        return parsePemCertPath(bytes, StandardCharsets.US_ASCII.decode(bytes))
    }

    /**
     * Parse the given string representing a cert path encoded as concatented certificates in PEM format.
     */
    fun parsePemCertPath(string: String): CertPath {
        return parsePemCertPath(ByteBuffer.wrap(string.toByteArray(StandardCharsets.US_ASCII)), string)
    }

    private fun parsePemCertPath(bytes: ByteBuffer, chars: CharSequence): CertPath {
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
