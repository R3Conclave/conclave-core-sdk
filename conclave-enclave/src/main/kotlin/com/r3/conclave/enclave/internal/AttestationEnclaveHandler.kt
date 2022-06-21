package com.r3.conclave.enclave.internal

import com.r3.conclave.common.internal.*
import com.r3.conclave.common.internal.handler.Handler
import com.r3.conclave.common.internal.handler.Sender
import com.r3.conclave.utilities.internal.getNullable
import java.nio.ByteBuffer

/**
 * The enclave side of the attestation protocol, which is the same whether EPID or DCAP is used. It receives report
 * requests from the host, containing the target Quoting Enclave's information, and sends back a report.
 */
abstract class AttestationEnclaveHandler(private val env: EnclaveEnvironment) : Handler<AttestationEnclaveHandler> {
    private lateinit var sender: Sender

    abstract val defaultReportData: ByteCursor<SgxReportData>

    private var _defaultReport: ByteCursor<SgxReport>? = null
    val defaultReport: ByteCursor<SgxReport> get() = checkNotNull(_defaultReport)

    override fun connect(upstream: Sender): AttestationEnclaveHandler {
        sender = upstream
        return this
    }

    override fun onReceive(connection: AttestationEnclaveHandler, input: ByteBuffer) {
        // We use Cursor.slice rather than Cursor.copy to avoid copying the bytes. The quotingEnclaveTargetInfo nor the
        // reportData are not used after this method return, so it's safe to do so.
        val quotingEnclaveTargetInfo = Cursor.slice(SgxTargetInfo, input)
        val reportData = input.getNullable { Cursor.slice(SgxReportData, input) }

        val report = env.createReport(quotingEnclaveTargetInfo, reportData ?: defaultReportData)
        if (reportData == null) {
            // Cache the report only if the default report data was used
            _defaultReport = report
        }
        sendReport(report)
    }

    private fun sendReport(report: ByteCursor<SgxReport>) {
        sender.send(report.size) { buffer ->
            buffer.put(report.buffer)
        }
    }
}
