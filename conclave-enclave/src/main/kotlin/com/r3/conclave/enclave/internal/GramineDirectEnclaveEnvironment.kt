package com.r3.conclave.enclave.internal

import com.r3.conclave.common.EnclaveMode
import com.r3.conclave.common.internal.*
import com.r3.conclave.utilities.internal.digest
import java.nio.ByteBuffer
import java.security.MessageDigest

/**
 * This class is the enclave environment intended for use with gramine-direct.
 */
class GramineDirectEnclaveEnvironment(
        enclaveClass: Class<*>,
        override val hostInterface: SocketEnclaveHostInterface,
        private val mrsigner: ByteArray
) : EnclaveEnvironment(loadEnclaveProperties(enclaveClass, false), null) {
    companion object {
        private fun versionToCpuSvn(num: Int): ByteArray {
            return digest("SHA-256") {
                update(ByteBuffer.allocate(2).putShort(num.toShort()).array())
            }.copyOf(SgxCpuSvn.size)
        }
    }

    // TODO: (CON-1194) Handle enclave mode properly
    override val enclaveMode = EnclaveMode.SIMULATION

    private val tcbLevel = 1

    private val currentCpuSvn: ByteArray by lazy {
        versionToCpuSvn(tcbLevel)
    }

    /** Generate simulated mrenclave value by hashing the enclave fat-jar */
    private val mrenclave: ByteArray by lazy {
        val digest = MessageDigest.getInstance("SHA-256")

        val buffer = ByteArray(65536)
        var bytesRead: Int

        enclaveClass.protectionDomain.codeSource.location.openStream().use {
            bytesRead = it.read(buffer)
            while (bytesRead >= 0) {
                digest.update(buffer, 0, bytesRead)
                bytesRead = it.read(buffer)
            }
        }

        digest.digest()
    }

    private val aesSealingKey by lazy(LazyThreadSafetyMode.NONE) {
        digest("SHA-256") { update(enclaveClass.name.toByteArray()) }.copyOf(16)
    }

    override fun createReport(
            targetInfo: ByteCursor<SgxTargetInfo>?,
            reportData: ByteCursor<SgxReportData>?
    ): ByteCursor<SgxReport> {
        val report = Cursor.allocate(SgxReport)
        val body = report[SgxReport.body]
        if (reportData != null) {
            body[SgxReportBody.reportData] = reportData.buffer
        }
        body[SgxReportBody.cpuSvn] = ByteBuffer.wrap(currentCpuSvn)
        body[SgxReportBody.mrenclave] = ByteBuffer.wrap(mrenclave)
        body[SgxReportBody.mrsigner] = ByteBuffer.wrap(mrsigner)
        body[SgxReportBody.isvProdId] = productID
        // Revocation level in the report is 1 based. We subtract 1 from it when reading it back from the report.
        body[SgxReportBody.isvSvn] = revocationLevel + 1
        body[SgxReportBody.attributes][SgxAttributes.flags] = SgxEnclaveFlags.DEBUG
        return report
    }

    override fun sealData(toBeSealed: PlaintextAndEnvelope): ByteArray {
        return EnclaveUtils.sealData(aesSealingKey, toBeSealed)
    }

    override fun unsealData(sealedBlob: ByteBuffer): PlaintextAndEnvelope {
        return EnclaveUtils.unsealData(aesSealingKey, sealedBlob)
    }

    override fun getSecretKey(keyRequest: ByteCursor<SgxKeyRequest>): ByteArray {
        val keyPolicy = keyRequest[SgxKeyRequest.keyPolicy]
        // TODO This is temporary: https://github.com/intel/linux-sgx/issues/578
        require(!keyPolicy.isSet(KeyPolicy.NOISVPRODID)) {
            "SGX_ERROR_INVALID_PARAMETER: The parameter is incorrect"
        }

        val keyName = keyRequest[SgxKeyRequest.keyName].read()
        if (keyName == KeyName.REPORT) {
            return digest("SHA-256") {
                update(mrsigner)
                update(mrenclave)
                update(keyRequest[SgxKeyRequest.keyId].buffer)
            }.copyOf(16)
        }

        require(keyName == KeyName.SEAL) { "Unsupported KeyName $keyName" }

        require(keyRequest[SgxKeyRequest.isvSvn].read() <= (revocationLevel + 1)) {
            "SGX_ERROR_INVALID_ISVSVN: The isv svn is greater than the enclave's isv svn"
        }

        val cpuSvn = keyRequest[SgxKeyRequest.cpuSvn].bytes
        require(cpuSvn.all { it.toInt() == 0 } || MockEnclaveEnvironment.isValidCpuSvn(tcbLevel, cpuSvn)) {
            "SGX_ERROR_INVALID_CPUSVN: The cpu svn is beyond platform's cpu svn value"
        }

        return digest("SHA-256") {
            if (keyPolicy.isSet(KeyPolicy.MRENCLAVE)) {
                update(mrenclave)
            }
            if (keyPolicy.isSet(KeyPolicy.MRSIGNER)) {
                update(mrsigner)
            }
            update(ByteBuffer.allocate(2).putShort(productID.toShort()).array())  // Product Id is an unsigned short.
            update(keyRequest[SgxKeyRequest.isvSvn].buffer)
            update(cpuSvn)
            update(keyRequest[SgxKeyRequest.keyId].buffer)
        }.copyOf(16)
    }

    override fun setupFileSystems(inMemoryFsSize: Long, persistentFsSize: Long, inMemoryMountPath: String, persistentMountPath: String, encryptionKey: ByteArray) {
        // TODO: Gramine filesystem support
    }
}
