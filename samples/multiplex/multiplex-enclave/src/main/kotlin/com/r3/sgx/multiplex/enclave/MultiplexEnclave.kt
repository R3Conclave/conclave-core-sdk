package com.r3.sgx.multiplex.enclave

import com.r3.sgx.core.common.*
import com.r3.sgx.core.enclave.EnclaveApi
import com.r3.sgx.core.enclave.Enclavelet
import java.nio.ByteBuffer

class MultiplexEnclave : Enclavelet() {
    override fun createReportData(api: EnclaveApi): Cursor<ByteBuffer, SgxReportData> {
        return Cursor.allocate(SgxReportData)
    }

    override fun createHandler(api: EnclaveApi): MultiplexEnclaveHandler {
        return MultiplexEnclaveHandler(api)
    }
}
