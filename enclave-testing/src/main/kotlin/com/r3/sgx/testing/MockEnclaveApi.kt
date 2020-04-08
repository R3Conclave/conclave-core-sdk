package com.r3.sgx.testing

import com.r3.conclave.common.internal.Cursor
import com.r3.conclave.common.internal.SgxAttributes.flags
import com.r3.conclave.common.internal.SgxEnclaveFlags
import com.r3.conclave.common.internal.SgxReport
import com.r3.conclave.common.internal.SgxReport.body
import com.r3.conclave.common.internal.SgxReportBody.attributes
import com.r3.conclave.common.internal.SgxReportBody.reportData
import com.r3.sgx.core.enclave.Enclave
import com.r3.sgx.core.enclave.EnclaveApi
import java.nio.ByteBuffer
import java.util.*

class MockEnclaveApi(val enclave: Enclave, private val simulation: Boolean) : EnclaveApi {
    constructor(enclave: Enclave) : this(enclave, false)

    override fun isSimulation(): Boolean = simulation

    override fun isDebugMode(): Boolean = true

    override fun createReport(targetInfoIn: ByteArray?, reportDataIn: ByteArray?, reportOut: ByteArray) {
        val report = Cursor(SgxReport, reportOut)
        if (reportDataIn != null) {
            report[body][reportData] = ByteBuffer.wrap(reportDataIn)
        }
        report[body][attributes][flags] = SgxEnclaveFlags.DEBUG
    }

    override fun getEnclaveClassName(): String = enclave.javaClass.name

    override fun getRandomBytes(output: ByteArray, offset: Int, length: Int) {
        val rng = Random()
        val bytes = ByteArray(length)
        rng.nextBytes(bytes)
        System.arraycopy(bytes, 0, output, offset, length)
    }
}
