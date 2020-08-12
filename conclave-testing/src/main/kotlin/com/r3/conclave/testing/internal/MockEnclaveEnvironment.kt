package com.r3.conclave.testing.internal

import com.r3.conclave.common.EnclaveMode
import com.r3.conclave.common.OpaqueBytes
import com.r3.conclave.common.internal.*
import com.r3.conclave.common.internal.KeyName.REPORT
import com.r3.conclave.common.internal.KeyName.SEAL
import com.r3.conclave.common.internal.KeyPolicy.MRENCLAVE
import com.r3.conclave.common.internal.KeyPolicy.MRSIGNER
import com.r3.conclave.common.internal.KeyPolicy.NOISVPRODID
import com.r3.conclave.common.internal.SgxAttributes.flags
import com.r3.conclave.common.internal.SgxReport.body
import com.r3.conclave.common.internal.SgxReportBody.attributes
import com.r3.conclave.common.internal.SgxReportBody.reportData
import com.r3.conclave.enclave.Enclave
import com.r3.conclave.enclave.internal.EnclaveEnvironment
import com.r3.conclave.utilities.internal.digest
import com.r3.conclave.utilities.internal.getBytes
import java.nio.ByteBuffer
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import kotlin.LazyThreadSafetyMode.NONE

class MockEnclaveEnvironment(
        private val enclave: Enclave,
        private val isvProdId: Int = 1,
        private val isvSvn: Int = 1
) : EnclaveEnvironment {
    private companion object {
        private const val IV_SIZE = 12
        private const val TAG_SIZE = 16

        private val secureRandom = SecureRandom()

        // All enclaves share the same CPUSVN.
        private val cpuSvn = ByteArray(SgxCpuSvn.size).also(secureRandom::nextBytes)
    }

    private val mrenclave = digest("SHA-256") { update(enclave.javaClass.name.toByteArray()) }
    private val cipher by lazy(NONE) { Cipher.getInstance("AES/GCM/NoPadding") }
    private val keySpec by lazy(NONE) {
        SecretKeySpec(digest("SHA-256") { update(enclave.javaClass.name.toByteArray()) }, "AES")
    }

    override val enclaveMode: EnclaveMode
        get() = EnclaveMode.MOCK

    override fun createReport(targetInfoIn: ByteArray?, reportDataIn: ByteArray?, reportOut: ByteArray) {
        val report = Cursor.wrap(SgxReport, reportOut)
        val body = report[body]
        if (reportDataIn != null) {
            body[reportData] = ByteBuffer.wrap(reportDataIn)
        }
        body[SgxReportBody.cpuSvn] = ByteBuffer.wrap(cpuSvn)
        body[SgxReportBody.mrenclave] = ByteBuffer.wrap(mrenclave)
        body[SgxReportBody.isvProdId] = isvProdId
        body[SgxReportBody.isvSvn] = isvSvn
        body[attributes][flags] = SgxEnclaveFlags.DEBUG
    }

    override fun randomBytes(output: ByteArray, offset: Int, length: Int) {
        if (offset == 0 && length == output.size) {
            secureRandom.nextBytes(output)
        } else {
            val bytes = ByteArray(length)
            secureRandom.nextBytes(bytes)
            System.arraycopy(bytes, 0, output, offset, length)
        }
    }

    @Synchronized
    override fun sealData(toBeSealed: PlaintextAndEnvelope): ByteArray {
        val iv = ByteArray(IV_SIZE).also(secureRandom::nextBytes)
        cipher.init(Cipher.ENCRYPT_MODE, keySpec, GCMParameterSpec(TAG_SIZE * 8, iv))
        val authenticatedData = toBeSealed.authenticatedData
        val sealedBlob = ByteBuffer.allocate(IV_SIZE + Int.SIZE_BYTES + (authenticatedData?.size ?: 0) + toBeSealed.plaintext.size + TAG_SIZE)
        sealedBlob.put(iv)
        if (authenticatedData != null) {
            cipher.updateAAD(authenticatedData.buffer())
            sealedBlob.putInt(authenticatedData.size)
            sealedBlob.put(authenticatedData.buffer())
        } else {
            sealedBlob.putInt(0)
        }
        cipher.doFinal(toBeSealed.plaintext.buffer(), sealedBlob)
        return sealedBlob.array()
    }

    @Synchronized
    override fun unsealData(sealedBlob: ByteArray): PlaintextAndEnvelope {
        cipher.init(Cipher.DECRYPT_MODE, keySpec, GCMParameterSpec(TAG_SIZE * 8, sealedBlob, 0, IV_SIZE))
        val inputBuffer = ByteBuffer.wrap(sealedBlob, IV_SIZE, sealedBlob.size - IV_SIZE)
        val authenticatedDataSize = inputBuffer.getInt()
        val authenticatedData = if (authenticatedDataSize > 0) {
            inputBuffer.getBytes(authenticatedDataSize).also(cipher::updateAAD)
        } else {
            null
        }
        val plaintext = ByteBuffer.allocate(inputBuffer.remaining() - TAG_SIZE)
        cipher.doFinal(inputBuffer, plaintext)
        return PlaintextAndEnvelope(OpaqueBytes(plaintext.array()), authenticatedData?.let(::OpaqueBytes))
    }

    // Replicates sgx_get_key behaviour in hardware as determined by MockSecretKeyHardwareCompatibilityTest.
    override fun getSecretKey(keyRequest: ByteCursor<SgxKeyRequest>): ByteArray {
        val keyPolicy = keyRequest[SgxKeyRequest.keyPolicy]
        // TODO This is temporary: https://github.com/intel/linux-sgx/issues/578
        require(!keyPolicy.isSet(NOISVPRODID)) {
            "SGX_ERROR_INVALID_PARAMETER: The parameter is incorrect"
        }

        val keyName = keyRequest[SgxKeyRequest.keyName].read()
        if (keyName == REPORT) {
            return digest("SHA-256") {
                update(mrenclave)
                update(keyRequest[SgxKeyRequest.keyId].buffer)
            }.copyOf(16)
        }

        require(keyName == SEAL) { "Unsupported KeyName $keyName" }

        require(keyRequest[SgxKeyRequest.isvSvn].read() <= isvSvn) {
            "SGX_ERROR_INVALID_ISVSVN: The isv svn is greater than the enclave's isv svn"
        }

        val cpuSvn = keyRequest[SgxKeyRequest.cpuSvn].bytes
        require(cpuSvn.all { it.toInt() == 0 } || cpuSvn.contentEquals(Companion.cpuSvn)) {
            "SGX_ERROR_INVALID_CPUSVN: The cpu svn is beyond platform's cpu svn value"
        }

        return digest("SHA-256") {
            if (keyPolicy.isSet(MRENCLAVE)) {
                update(mrenclave)
            }
            if (keyPolicy.isSet(MRSIGNER)) {
                update(0)  // Mock enclaves don't have a code signer so this is sufficient for deriving a MRSIGNER key.
            }
            update(ByteBuffer.allocate(2).putShort(isvProdId.toShort()).array())  // Product Id is an unsigned short.
            update(keyRequest[SgxKeyRequest.isvSvn].buffer)
            update(cpuSvn)
            update(keyRequest[SgxKeyRequest.keyId].buffer)
        }.copyOf(16)
    }
}
