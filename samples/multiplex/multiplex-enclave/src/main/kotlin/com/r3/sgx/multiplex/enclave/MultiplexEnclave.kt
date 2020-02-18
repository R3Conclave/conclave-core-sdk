package com.r3.sgx.multiplex.enclave

import com.r3.sgx.core.common.ByteCursor
import com.r3.sgx.core.common.Cursor
import com.r3.sgx.core.common.SgxReportData
import com.r3.sgx.core.enclave.EnclaveApi
import com.r3.sgx.core.enclave.Enclavelet

class MultiplexEnclave : Enclavelet() {
    override fun createReportData(api: EnclaveApi): ByteCursor<SgxReportData> {
        return Cursor.allocate(SgxReportData)
    }

    override fun createHandler(api: EnclaveApi): MultiplexEnclaveHandler {
        return MultiplexEnclaveHandler(api)
    }
}
