package com.r3.sgx.multiplex.enclave

import com.r3.conclave.common.internal.ByteCursor
import com.r3.conclave.common.internal.Cursor
import com.r3.conclave.common.internal.SgxReportData
import com.r3.sgx.core.common.*
import com.r3.sgx.core.enclave.EnclaveApi
import com.r3.sgx.core.enclave.Enclavelet
import java.nio.ByteBuffer
import java.util.function.Consumer

class ShoutingEnclavelet : Enclavelet() {
    override fun createReportData(api: EnclaveApi): ByteCursor<SgxReportData> {
        return Cursor.allocate(SgxReportData)
    }

    override fun createHandler(api: EnclaveApi) = ShoutingHandler()

    class ShoutingHandler : Handler<Sender> {
        override fun connect(upstream: Sender) = upstream

        override fun onReceive(connection: Sender, input: ByteBuffer) {
            val data = ByteArray(input.remaining())
            input.get(data)
            val newData = String(data).toUpperCase().toByteArray()
            connection.send(newData.size, Consumer { buffer ->
                buffer.put(newData)
            })
        }
    }
}

