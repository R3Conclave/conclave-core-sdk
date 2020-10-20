package com.r3.conclave.enclave.internal

import com.r3.conclave.common.internal.*
import com.r3.conclave.common.internal.handler.Handler
import com.r3.conclave.common.internal.handler.Sender
import java.nio.ByteBuffer

/**
 * The enclave side of the EPID attestation protocol. It receives report requests from the host, containing the target
 * Quoting Enclave's information, and sends back a report.
 */
abstract class EpidAttestationEnclaveHandler(private val env: EnclaveEnvironment) : Handler<EpidAttestationEnclaveHandler> {
    private lateinit var sender: Sender

    abstract val reportData: ByteCursor<SgxReportData>

    private var _report: ByteCursor<SgxReport>? = null
    val report: ByteCursor<SgxReport> get() = checkNotNull(_report)

    override fun connect(upstream: Sender): EpidAttestationEnclaveHandler {
        sender = upstream
        return this
    }

    override fun onReceive(connection: EpidAttestationEnclaveHandler, input: ByteBuffer) {
        check(_report == null)
        val quotingEnclaveTargetInfo = Cursor.read(SgxTargetInfo, input)
        val report = env.createReport(quotingEnclaveTargetInfo, reportData)
        _report = report
        sender.send(report.encoder.size) { buffer ->
            buffer.put(report.buffer)
        }
    }
}
