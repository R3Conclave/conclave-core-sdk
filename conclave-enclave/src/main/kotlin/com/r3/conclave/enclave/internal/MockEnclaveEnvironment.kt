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
import com.r3.conclave.enclave.Enclave
import com.r3.conclave.utilities.internal.digest
import java.nio.ByteBuffer
import java.security.SecureRandom
import kotlin.LazyThreadSafetyMode.NONE

class MockEnclaveEnvironment(
    enclave: Enclave,
    mockConfiguration: MockConfiguration?
) : EnclaveEnvironment(loadEnclaveProperties(enclave::class.java, true)) {
    companion object {
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

    private val configuration = mockConfiguration ?: MockConfiguration()

    // Our configuration stores the CPUSVN as an integer for simplicity. Hash this to a byte array.
    private val currentCpuSvn: ByteArray by lazy {
        versionToCpuSvn(tcbLevel)
    }

    private val mrenclave: ByteArray by lazy {
        // Use the value from the mock configuration if provided,  otherwise hardcode it
        // to a hash of the java class name.
        configuration.codeHash?.bytes ?: digest("SHA-256") { update(enclave.javaClass.name.toByteArray()) }
    }

    private val mrsigner: ByteArray by lazy {
        configuration.codeSigningKeyHash?.bytes ?: ByteArray(32)
    }

    private val sealingSecret by lazy(NONE) {
        digest("SHA-256") { update(enclave.javaClass.name.toByteArray()) }
    }

    private val tcbLevel: Int
        get() = configuration.tcbLevel ?: 1

    override val productID: Int
        get() = configuration.productID ?: super.productID

    override val revocationLevel: Int
        get() = configuration.revocationLevel ?: super.revocationLevel

    override val enablePersistentMap: Boolean
        get() = configuration.enablePersistentMap ?: super.enablePersistentMap

    override val maxPersistentMapSize: Long
        get() = configuration.maxPersistentMapSize ?: super.maxPersistentMapSize

    override val enclaveMode: EnclaveMode
        get() = EnclaveMode.MOCK

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
        body[SgxReportBody.isvProdId] = productID
        // Revocation level in the report is 1 based. We subtract 1 from it when reading it back from the report.
        body[SgxReportBody.isvSvn] = revocationLevel + 1
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
        return EnclaveUtils.aesEncrypt(sealingSecret, toBeSealed)
    }

    @Synchronized
    override fun unsealData(sealedBlob: ByteArray): PlaintextAndEnvelope {
        return EnclaveUtils.aesDecrypt(sealingSecret, sealedBlob)
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

        require(keyRequest[SgxKeyRequest.isvSvn].read() <= (revocationLevel + 1)) {
            "SGX_ERROR_INVALID_ISVSVN: The isv svn is greater than the enclave's isv svn"
        }

        val cpuSvn = keyRequest[SgxKeyRequest.cpuSvn].bytes
        require(cpuSvn.all { it.toInt() == 0 } || isValidCpuSvn(tcbLevel, cpuSvn)) {
            "SGX_ERROR_INVALID_CPUSVN: The cpu svn is beyond platform's cpu svn value"
        }

        return digest("SHA-256") {
            if (keyPolicy.isSet(MRENCLAVE)) {
                update(mrenclave)
            }
            if (keyPolicy.isSet(MRSIGNER)) {
                update(mrsigner)
            }
            update(ByteBuffer.allocate(2).putShort(productID.toShort()).array())  // Product Id is an unsigned short.
            update(keyRequest[SgxKeyRequest.isvSvn].buffer)
            update(cpuSvn)
            update(keyRequest[SgxKeyRequest.keyId].buffer)
        }.copyOf(16)
    }

    override fun setupFileSystems(
        inMemoryFsSize: Long,
        persistentFsSize: Long,
        inMemoryMountPath: String,
        persistentMountPath: String,
        encryptionKey: ByteArray
    ) {
        //  NO op for Mock mode
    }
}
