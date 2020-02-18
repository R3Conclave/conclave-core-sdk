package com.r3.sgx.multiplex.enclave

import com.r3.sgx.core.common.*
import com.r3.sgx.core.enclave.EnclaveApi
import com.r3.sgx.core.enclave.Enclavelet
import java.nio.ByteBuffer
import java.util.function.Consumer

class LispingEnclavelet : Enclavelet() {
    override fun createReportData(api: EnclaveApi): ByteCursor<SgxReportData> {
        return Cursor.allocate(SgxReportData)
    }

    override fun createHandler(api: EnclaveApi) = LispingHandler()

    class LispingHandler : Handler<Sender> {
        override fun connect(upstream: Sender) = upstream

        override fun onReceive(connection: Sender, input: ByteBuffer) {
            val data = ByteArray(input.remaining()).apply { input.get(this) }
            val newData = String(data)
                .replace("Sh", "Th")
                .replace("sh", "th")
                .replace("s", "th")
                .replace("S", "Th")
                .toByteArray()
            connection.send(newData.size, Consumer { buffer ->
                buffer.put(newData)
            })
        }
    }
}