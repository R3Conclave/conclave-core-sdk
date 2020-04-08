package com.r3.sgx.multiplex.enclave

import com.r3.conclave.common.internal.ByteCursor
import com.r3.conclave.common.internal.Cursor
import com.r3.conclave.common.internal.SgxReportData
import com.r3.sgx.core.common.Handler
import com.r3.sgx.core.enclave.EnclaveApi
import com.r3.sgx.core.enclave.Enclavelet

class BrokenEnclavelet : Enclavelet() {
    class BrokenException(message: String) : Exception(message)

    override fun createReportData(api: EnclaveApi): ByteCursor<SgxReportData> {
        return Cursor.allocate(SgxReportData)
    }

    override fun createHandler(api: EnclaveApi): Handler<*> {
        throw BrokenException("CANNOT START!")
    }
}