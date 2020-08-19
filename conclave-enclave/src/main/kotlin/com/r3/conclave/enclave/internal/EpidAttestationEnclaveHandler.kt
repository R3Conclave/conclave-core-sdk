package com.r3.conclave.enclave.internal

import com.r3.conclave.common.internal.ByteCursor
import com.r3.conclave.common.internal.Cursor
import com.r3.conclave.common.internal.SgxReportData
import com.r3.conclave.common.internal.SgxTargetInfo
import com.r3.conclave.common.internal.handler.Handler
import com.r3.conclave.common.internal.handler.Sender
import java.nio.ByteBuffer
import java.util.function.Consumer

/**
 * The enclave side of the EPID attestation protocol. It receives report requests from the host, containing the target
 * Quoting Enclave's information, and sends back a report.
 */
abstract class EpidAttestationEnclaveHandler(private val env: EnclaveEnvironment) : Handler<Sender> {
    abstract val reportData: ByteCursor<SgxReportData>

    override fun connect(upstream: Sender): Sender = upstream

    override fun onReceive(connection: Sender, input: ByteBuffer) {
        val quotingEnclaveTargetInfo = Cursor.read(SgxTargetInfo, input)
        val report = env.createReport(quotingEnclaveTargetInfo, reportData)
        connection.send(report.encoder.size, Consumer { buffer ->
            buffer.put(report.buffer)
        })
    }
}
