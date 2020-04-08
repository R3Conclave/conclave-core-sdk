package com.r3.sgx.djvm.enclave

import com.r3.conclave.common.internal.ByteCursor
import com.r3.conclave.common.internal.Cursor
import com.r3.conclave.common.internal.SgxReportData
import com.r3.sgx.core.common.Handler
import com.r3.sgx.core.enclave.EnclaveApi
import com.r3.sgx.core.enclave.Enclavelet
import com.r3.sgx.djvm.enclave.handlers.DJVMHandler

class DJVMEnclave : Enclavelet() {
    override fun createReportData(api: EnclaveApi): ByteCursor<SgxReportData> {
        val report = Cursor.allocate(SgxReportData)
        val buffer = report.getBuffer()
        buffer.put(ByteArray(buffer.capacity()) {0})
        return report
    }

    override fun createHandler(api: EnclaveApi): Handler<*> {
        return DJVMHandler()
    }
}