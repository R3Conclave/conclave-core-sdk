package com.r3.conclave.common.internal.attestation

import com.r3.conclave.common.OpaqueBytes
import com.r3.conclave.common.internal.SGXExtensionASN1Parser
import com.r3.conclave.common.internal.inputStream
import com.r3.conclave.utilities.internal.getBytes
import java.io.DataOutputStream
import java.io.InputStream
import java.math.BigInteger
import java.nio.ByteBuffer
import java.security.cert.CertPath
import java.security.cert.Certificate
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate

object AttestationUtils {
    private const val SGX_EXTENSION_OID = "1.2.840.113741.1.13.1"

    const val SGX_TCB_OID = "$SGX_EXTENSION_OID.2"
    const val SGX_PCESVN_OID = "$SGX_TCB_OID.17"
    const val SGX_PCEID_OID = "$SGX_EXTENSION_OID.3"
    const val SGX_FMSPC_OID = "$SGX_EXTENSION_OID.4"

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

    fun parsePemCertPath(buffer: ByteBuffer, trailingBytes: Int = 0): CertPath {
        return parsePemCertPath(buffer.inputStream(), trailingBytes)
    }

    fun parsePemCertPath(stream: InputStream, trailingBytes: Int = 0): CertPath {
        val certificateFactory = CertificateFactory.getInstance("X.509")
        // The cert path length in DCAP is 3, so we round that up to 4 for the initial capacity and avoid internal
        // copying.
        val certificates = ArrayList<Certificate>(4)
        while (stream.available() > trailingBytes) {
            certificates += certificateFactory.generateCertificate(stream)
        }
        return certificateFactory.generateCertPath(certificates)
    }

    fun DataOutputStream.writeIntLengthPrefixBytes(bytes: OpaqueBytes) {
        writeInt(bytes.size)
        bytes.writeTo(this)
    }
}
