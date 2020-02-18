package com.r3.sgx.enclave.echo

import com.r3.sgx.core.common.*
import com.r3.sgx.core.enclave.EnclaveApi
import com.r3.sgx.core.enclave.Enclavelet
import java.nio.ByteBuffer
import java.util.function.Consumer

internal class EchoHandler : Handler<Sender> {
    override fun onReceive(connection: Sender, input: ByteBuffer) {
        connection.send(input.remaining(), Consumer { buffer ->
            buffer.put(input)
        })
    }

    override fun connect(upstream: Sender) = upstream
}

/**
 * An enclave that simply echoes the input to the enclavelet
 */
class EchoEnclave : Enclavelet() {
    override fun createReportData(api: EnclaveApi): ByteCursor<SgxReportData> {
        val report = Cursor.allocate(SgxReportData)
        val buffer = report.getBuffer()
        buffer.put(ByteArray(buffer.capacity()) {0})
        return report
    }

    override fun createHandler(api: EnclaveApi): Handler<*> {
        return EchoHandler()
    }
}
