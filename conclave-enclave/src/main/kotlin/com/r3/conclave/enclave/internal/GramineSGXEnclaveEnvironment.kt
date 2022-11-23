package com.r3.conclave.enclave.internal

import com.r3.conclave.common.EnclaveMode
import com.r3.conclave.common.internal.*
import com.r3.conclave.utilities.internal.digest
import com.r3.conclave.utilities.internal.getRemainingBytes
import java.nio.ByteBuffer
import java.nio.file.Paths
import kotlin.io.path.readBytes
import kotlin.io.path.writeBytes

/**
 * This class is the enclave environment intended for use with gramine-sgx.
 */
class GramineSGXEnclaveEnvironment(
    enclaveClass: Class<*>,
    override val hostInterface: SocketEnclaveHostInterface,
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
        require(enclaveMode.isHardware) { "Gramine SGX can't run in MOCK or SIMULATION mode" }
    }

    override fun createReport(
        targetInfo: ByteCursor<SgxTargetInfo>?,
        reportData: ByteCursor<SgxReportData>?
    ): ByteCursor<SgxReport> {
        val report = retrieveReport(
            targetInfo?.buffer?.getRemainingBytes(avoidCopying = true) ?: byteArrayOf(),
            reportData?.buffer?.getRemainingBytes(avoidCopying = true) ?: byteArrayOf()
        )
        return Cursor.slice(SgxReport, ByteBuffer.wrap(report))
    }

    override fun getSignedQuote(
        quotingEnclaveInfo: ByteCursor<SgxTargetInfo>?,
        reportData: ByteCursor<SgxReportData>?
    ): ByteCursor<SgxSignedQuote> {
        //  Note that in Gramine the "signed quote" is automatically retrieved and returned by the enclave.
        //    There is no "enclave to host" communication that we need to handle in our code.
        //    In the background, Gramine interacts with the AESM service and the quoting enclave
        //    to get the "signed quote".
        //  In Graal VM/Native Image flow the "quotingEnclaveInfo" (also called "SGX target info") is
        //    requested by the host to the quoting enclave by passing an empty array to the
        //    function "sgx_get_target_info", which fills that array.
        //  When working with the Gramine flow, such operation is done automatically when accessing
        //    the /dev/attestation/quote pseudofile.
        //  Note that Gramine uses the quotingEnclaveInfo of the quoting enclave and does not read the one that
        //    might have been written into /dev/attestation/target_info, see the sgx_get_quote function in Gramine code.
        createReport(null, reportData)
        val signedQuoteBytes = readSignedQuote()
        return Cursor.slice(SgxSignedQuote, ByteBuffer.wrap(signedQuoteBytes))
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

    @Synchronized
    private fun retrieveReport(targetInfoBytes: ByteArray, userReportDataBytes: ByteArray): ByteArray {
        writeTargetInfo(targetInfoBytes)
        writeUserReportData(userReportDataBytes)
        return readReport()
    }

    private fun readReport(): ByteArray {
        return Paths.get("/dev/attestation/report").readBytes()
    }

    private fun readSignedQuote(): ByteArray {
        return Paths.get("/dev/attestation/quote").readBytes()
    }

    private fun writeUserReportData(data: ByteArray) {
        return Paths.get("/dev/attestation/user_report_data").writeBytes(data)
    }

    private fun writeTargetInfo(data: ByteArray) {
        return Paths.get("/dev/attestation/target_info").writeBytes(data)
    }

    private val aesSealingKey by lazy(LazyThreadSafetyMode.NONE) {
        digest("SHA-256") { update(enclaveClass.name.toByteArray()) }.copyOf(16)
    }
}
