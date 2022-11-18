package com.r3.conclave.enclave.internal

import com.r3.conclave.common.EnclaveMode
import com.r3.conclave.common.SHA256Hash
import com.r3.conclave.common.internal.*
import com.r3.conclave.utilities.internal.digest
import com.r3.conclave.utilities.internal.getRemainingBytes
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.security.MessageDigest

/**
 * This class is the enclave environment intended for use with gramine-direct.
 */
class GramineEnclaveEnvironment(
    enclaveClass: Class<*>,
    override val hostInterface: SocketEnclaveHostInterface,
    private val simulationMrsigner: SHA256Hash?,
    override val enclaveMode: EnclaveMode
) : EnclaveEnvironment(loadEnclaveProperties(enclaveClass, false), null) {
    companion object {
        private fun versionToCpuSvn(num: Int): ByteArray {
            return digest("SHA-256") {
                update(ByteBuffer.allocate(2).putShort(num.toShort()).array())
            }.copyOf(SgxCpuSvn.size)
        }
    }

    init {
        require(enclaveMode != EnclaveMode.MOCK) { "Gramine can't run in MOCK mode" }
    }

    override fun createReport(
        targetInfo: ByteCursor<SgxTargetInfo>?,
        reportData: ByteCursor<SgxReportData>?
    ): ByteCursor<SgxReport> {
        return if (enclaveMode == EnclaveMode.SIMULATION) {
            createSimulationReport(reportData)
        } else {
            val report = retrieveReport(
                targetInfo?.buffer?.getRemainingBytes(avoidCopying = true) ?: byteArrayOf(),
                reportData?.buffer?.getRemainingBytes(avoidCopying = true) ?: byteArrayOf()
            )
            Cursor.slice(SgxReport, ByteBuffer.wrap(report))
        }
    }

    override fun getSignedQuote(
        quotingEnclaveInfo: ByteCursor<SgxTargetInfo>?,
        reportData: ByteCursor<SgxReportData>?
    ): ByteCursor<SgxSignedQuote> {
        //  Note that in Gramine the "signed quote" is automatically retrieved and returned
        //    by the enclave. There is no enclave to host communication that we need to handle in our code.
        //    In the background, Gramine interacts with the AESM service and the quoting enclave
        //    to get the "signed quote".
        return if (enclaveMode == EnclaveMode.SIMULATION) {
            val report = createSimulationReport(reportData)
            val signedQuote = Cursor.wrap(SgxSignedQuote, ByteArray(SgxSignedQuote.minSize))
            signedQuote[SgxSignedQuote.quote][SgxQuote.reportBody] = report[SgxReport.body].read()
            return signedQuote
        } else {
            //  In Graal VM/Native Image flow the "quotingEnclaveInfo" (also called "SGX target info") is
            //    requested by the host to the quoting enclave by passing an empty array to the
            //    function "sgx_get_target_info", which fills that array.
            //  When working with the Gramine flow, such operation is done on the enclave side by passing an empty
            //    array that it is filled by Gramine (which communicates with the quoting enclave in background).
            //  Here we reuse this
            val quotingEnclaveInfoBytes = quotingEnclaveInfo?.bytes ?: Cursor.allocate(SgxTargetInfo).bytes
            createReport(ByteCursor.wrap(SgxTargetInfo, quotingEnclaveInfoBytes), reportData)
            val signedQuoteBytes = readSignedQuote()
            Cursor.slice(SgxSignedQuote, ByteBuffer.wrap(signedQuoteBytes))
        }
    }

    override fun sealData(toBeSealed: PlaintextAndEnvelope): ByteArray {
        //  TODO: Gramine filesystem support
        return EnclaveUtils.sealData(aesSealingKey, toBeSealed)
    }

    override fun unsealData(sealedBlob: ByteBuffer): PlaintextAndEnvelope {
        //  TODO: Gramine filesystem support
        return EnclaveUtils.unsealData(aesSealingKey, sealedBlob)
    }

    override fun getSecretKey(keyRequest: ByteCursor<SgxKeyRequest>): ByteArray {
        //  TODO: Gramine filesystem support
        //  Note that we will probably not need to pass around the local secret key
        //    as this one will be embedded in the manifest
        //  https://gramine.readthedocs.io/en/stable/manifest-syntax.html#encrypted-files
        return byteArrayOf()
    }

    override fun setupFileSystems(
        inMemoryFsSize: Long,
        persistentFsSize: Long,
        inMemoryMountPath: String,
        persistentMountPath: String,
        encryptionKey: ByteArray
    ) {
        //  TODO: Gramine filesystem support
    }

    private fun retrieveReport(targetInfoBytes: ByteArray, userReportDataBytes: ByteArray): ByteArray {
        writeTargetInfo(targetInfoBytes)
        writeUserReportData(userReportDataBytes)
        return readReport()
    }

    private fun readReport(): ByteArray {
        return FileInputStream("/dev/attestation/report").use {
            it.readBytes()
        }
    }

    private fun readSignedQuote(): ByteArray {
        return FileInputStream("/dev/attestation/quote").use {
            it.readBytes()
        }
    }

    private fun writeUserReportData(data: ByteArray) {

        return FileOutputStream("/dev/attestation/user_report_data").use {
            it.write(data)
        }
    }

    private fun writeTargetInfo(data: ByteArray) {
        FileOutputStream("/dev/attestation/target_info").use {
            it.write(data)
        }
    }

    private fun createSimulationReport(
        reportData: ByteCursor<SgxReportData>?
    ): ByteCursor<SgxReport> {
        check(simulationMrsigner != null) { "Simulation MRSigner not provided in the enclave" }
        val tcbLevel = 1
        val currentCpuSvn = versionToCpuSvn(tcbLevel)
        val report = Cursor.allocate(SgxReport)

        val body = report[SgxReport.body]
        if (reportData != null) {
            body[SgxReportBody.reportData] = reportData.buffer
        }
        body[SgxReportBody.cpuSvn] = ByteBuffer.wrap(currentCpuSvn)
        body[SgxReportBody.mrenclave] = ByteBuffer.wrap(simulationMrEnclave)
        body[SgxReportBody.mrsigner] = simulationMrsigner!!.buffer()
        body[SgxReportBody.isvProdId] = productID
        // Revocation level in the report is 1 based. We subtract 1 from it when reading it back from the report.
        body[SgxReportBody.isvSvn] = revocationLevel + 1
        body[SgxReportBody.attributes][SgxAttributes.flags] = SgxEnclaveFlags.DEBUG
        return report
    }

    private val simulationMrEnclave: ByteArray by lazy {
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
}
