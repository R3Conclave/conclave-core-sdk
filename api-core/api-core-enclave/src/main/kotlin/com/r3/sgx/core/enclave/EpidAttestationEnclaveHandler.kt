package com.r3.sgx.core.enclave

import com.r3.conclave.common.internal.*
import com.r3.sgx.core.common.Handler
import com.r3.sgx.core.common.Sender
import java.nio.ByteBuffer
import java.util.function.Consumer

/**
 * The enclave side of the EPID attestation protocol. It receives report requests from the host, containing the target
 * Quoting Enclave's information, and sends back a report.
 */
abstract class EpidAttestationEnclaveHandler(val api: EnclaveApi) : Handler<Sender> {
    abstract val reportData: ByteCursor<SgxReportData>

    override fun connect(upstream: Sender): Sender = upstream

    override fun onReceive(connection: Sender, input: ByteBuffer) {
        val quotingEnclaveTargetInfo = input.getRemainingBytes()
        val report = Cursor.allocate(SgxReport)
        api.createReport(quotingEnclaveTargetInfo, reportData.getBuffer().array(), report.getBuffer().array())
        connection.send(SgxReport.size(), Consumer { buffer ->
            buffer.put(report.getBuffer().array())
        })
    }
}
