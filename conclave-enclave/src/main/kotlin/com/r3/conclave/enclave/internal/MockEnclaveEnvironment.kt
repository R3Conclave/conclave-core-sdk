package com.r3.conclave.enclave.internal

import com.r3.conclave.common.EnclaveMode
import com.r3.conclave.common.MockConfiguration
import com.r3.conclave.common.internal.*
import com.r3.conclave.common.internal.KeyName.REPORT
import com.r3.conclave.common.internal.KeyName.SEAL
import com.r3.conclave.common.internal.KeyPolicy.MRENCLAVE
import com.r3.conclave.common.internal.KeyPolicy.MRSIGNER
import com.r3.conclave.common.internal.KeyPolicy.NOISVPRODID
import com.r3.conclave.common.internal.SgxAttributes.flags
import com.r3.conclave.common.internal.SgxReport.body
import com.r3.conclave.common.internal.SgxReportBody.attributes
import com.r3.conclave.utilities.internal.digest
import com.r3.conclave.utilities.internal.getBytes
import java.nio.ByteBuffer
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import kotlin.LazyThreadSafetyMode.NONE

class MockEnclaveEnvironment(
    private val enclave: Any,
    mockConfiguration: MockConfiguration?
) : EnclaveEnvironment {
    companion object {
        private const val IV_SIZE = 12
        private const val TAG_SIZE = 16

        private val secureRandom = SecureRandom()

        private fun versionToCpuSvn(num: Int): ByteArray { 
            return digest("SHA-256") { 
                update(ByteBuffer.allocate(2).putShort(num.toShort()).array()) 
            }.copyOf(SgxCpuSvn.size)
        }

        fun isValidCpuSvn(currentCpuSvnNumber: Int, cpuSvn: ByteArray): Boolean {
            // Any ISVSVN from 1 to the given number is valid
            for (version in currentCpuSvnNumber downTo 1) {
                if (versionToCpuSvn(version).contentEquals(cpuSvn)) {
                    return true
                }
            }
            return false
        }
    }

    // Use the configuration provided by the caller, or a default configuration
    private val configuration = mockConfiguration ?: MockConfiguration()

    // Our configuration stores the CPUSVN as an integer for simplicity. Hash this to a byte array.
    private val currentCpuSvn: ByteArray by lazy {
        versionToCpuSvn(configuration.tcbLevel)
    }

    private val mrenclave: ByteArray by lazy {
            // Use the value form the mock configuration if provided,  otherwise hardcode it
            // to a hash of the java class name.
            configuration.codeHash?.bytes ?: digest("SHA-256") { update(enclave.javaClass.name.toByteArray()) }
    }

    private val mrsigner: ByteArray by lazy {
        configuration.codeSigningKeyHash?.bytes ?: ByteArray(32)
    }

    private val cipher by lazy(NONE) { Cipher.getInstance("AES/GCM/NoPadding") }
    private val keySpec by lazy(NONE) {
        SecretKeySpec(digest("SHA-256") { update(enclave.javaClass.name.toByteArray()) }, "AES")
    }

    override val enclaveMode: EnclaveMode
        get() = EnclaveMode.MOCK

    override val enablePersistentMap: Boolean
        get() = configuration.enablePersistentMap

    override val maxPersistentMapSize: Long
        get() = configuration.maxPersistentMapSize

    override fun createReport(
        targetInfo: ByteCursor<SgxTargetInfo>?,
        reportData: ByteCursor<SgxReportData>?
    ): ByteCursor<SgxReport> {
        val report = Cursor.allocate(SgxReport)
        val body = report[body]
        if (reportData != null) {
            body[SgxReportBody.reportData] = reportData.buffer
        }
        body[SgxReportBody.cpuSvn] = ByteBuffer.wrap(currentCpuSvn)
        body[SgxReportBody.mrenclave] = ByteBuffer.wrap(mrenclave)
        body[SgxReportBody.mrsigner] = ByteBuffer.wrap(mrsigner)
        body[SgxReportBody.isvProdId] = configuration.productID
        // Revocation level in the report is 1 based. We subtract 1 from it when reading it back from the report.
        body[SgxReportBody.isvSvn] = configuration.revocationLevel + 1
        body[attributes][flags] = SgxEnclaveFlags.DEBUG
        return report
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
        val sealedBlob = ByteBuffer.allocate(
            IV_SIZE + Int.SIZE_BYTES + (authenticatedData?.size ?: 0) + toBeSealed.plaintext.size + TAG_SIZE
        )
        sealedBlob.put(iv)
        if (authenticatedData != null) {
            cipher.updateAAD(authenticatedData)
            sealedBlob.putInt(authenticatedData.size)
            sealedBlob.put(authenticatedData)
        } else {
            sealedBlob.putInt(0)
        }
        cipher.doFinal(ByteBuffer.wrap(toBeSealed.plaintext), sealedBlob)
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
        return PlaintextAndEnvelope(plaintext.array(), authenticatedData)
    }

    // Replicates sgx_get_key behaviour in hardware as determined by MockEnclaveEnvironmentHardwareCompatibilityTest.
    override fun getSecretKey(keyRequest: ByteCursor<SgxKeyRequest>): ByteArray {
        val keyPolicy = keyRequest[SgxKeyRequest.keyPolicy]
        // TODO This is temporary: https://github.com/intel/linux-sgx/issues/578
        require(!keyPolicy.isSet(NOISVPRODID)) {
            "SGX_ERROR_INVALID_PARAMETER: The parameter is incorrect"
        }

        val keyName = keyRequest[SgxKeyRequest.keyName].read()
        if (keyName == REPORT) {
            return digest("SHA-256") {
                update(mrsigner)
                update(mrenclave)
                update(keyRequest[SgxKeyRequest.keyId].buffer)
            }.copyOf(16)
        }

        require(keyName == SEAL) { "Unsupported KeyName $keyName" }

        require(keyRequest[SgxKeyRequest.isvSvn].read() <= (configuration.revocationLevel + 1)) {
            "SGX_ERROR_INVALID_ISVSVN: The isv svn is greater than the enclave's isv svn"
        }

        val cpuSvn = keyRequest[SgxKeyRequest.cpuSvn].bytes
        require(cpuSvn.all { it.toInt() == 0 } || isValidCpuSvn(configuration.tcbLevel, cpuSvn)) {
            "SGX_ERROR_INVALID_CPUSVN: The cpu svn is beyond platform's cpu svn value"
        }

        return digest("SHA-256") {
            if (keyPolicy.isSet(MRENCLAVE)) {
                update(mrenclave)
            }
            if (keyPolicy.isSet(MRSIGNER)) {
                update(mrsigner)
            }
            update(ByteBuffer.allocate(2).putShort(configuration.productID.toShort()).array())  // Product Id is an unsigned short.
            update(keyRequest[SgxKeyRequest.isvSvn].buffer)
            update(cpuSvn)
            update(keyRequest[SgxKeyRequest.keyId].buffer)
        }.copyOf(16)
    }
}
