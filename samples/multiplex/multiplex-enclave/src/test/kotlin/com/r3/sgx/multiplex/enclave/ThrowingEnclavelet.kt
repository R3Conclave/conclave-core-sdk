package com.r3.sgx.multiplex.enclave

import com.r3.sgx.core.common.*
import com.r3.sgx.core.enclave.EnclaveApi
import com.r3.sgx.core.enclave.Enclavelet
import java.nio.ByteBuffer
import kotlin.Exception

class ThrowingEnclavelet : Enclavelet() {
    override fun createReportData(api: EnclaveApi): ByteCursor<SgxReportData> {
        return Cursor.allocate(SgxReportData)
    }

    override fun createHandler(api: EnclaveApi) = ThrowingHandler()

    class FailingException(message: String): Exception(message)

    class ThrowingHandler : Handler<Sender> {
        override fun connect(upstream: Sender) = upstream
        override fun onReceive(connection: Sender, input: ByteBuffer) {
            val data = ByteArray(input.remaining())
            input.get(data)
            throw FailingException(String(data))
        }
    }
}