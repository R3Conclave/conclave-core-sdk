package com.r3.conclave.testing.internal

import com.r3.conclave.common.internal.*
import com.r3.conclave.common.internal.SgxAttributes.flags
import com.r3.conclave.common.internal.SgxReport.body
import com.r3.conclave.common.internal.SgxReportBody.attributes
import com.r3.conclave.common.internal.SgxReportBody.reportData
import com.r3.conclave.enclave.internal.EnclaveApi
import java.nio.ByteBuffer
import java.util.*

class MockEnclaveApi(
        private val isvProdId: Int = 1,
        private val isvSvn: Int = 1
) : EnclaveApi {
    override fun isSimulation(): Boolean = false

    override fun isDebugMode(): Boolean = true

    override fun createReport(targetInfoIn: ByteArray?, reportDataIn: ByteArray?, reportOut: ByteArray) {
        val report = Cursor(SgxReport, reportOut)
        val body = report[body]
        if (reportDataIn != null) {
            body[reportData] = ByteBuffer.wrap(reportDataIn)
        }
        body[SgxReportBody.isvProdId] = isvProdId
        body[SgxReportBody.isvSvn] = isvSvn
        body[attributes][flags] = SgxEnclaveFlags.DEBUG
    }

    override fun randomBytes(output: ByteArray, offset: Int, length: Int) {
        val rng = Random()
        val bytes = ByteArray(length)
        rng.nextBytes(bytes)
        System.arraycopy(bytes, 0, output, offset, length)
    }

    override fun defaultSealingKey(keyType: KeyType, useSigner: Boolean, cpuSvn: Boolean): ByteArray {
        return ByteArray(16).also { randomBytes(it, 0, it.size) }
    }
}
