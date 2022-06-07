package com.r3.conclave.host.internal

import com.r3.conclave.common.internal.*
import com.r3.conclave.common.internal.handler.Handler
import com.r3.conclave.common.internal.handler.Sender
import com.r3.conclave.host.AttestationParameters
import com.r3.conclave.host.internal.AttestationHostHandler.State.Ready
import com.r3.conclave.host.internal.AttestationHostHandler.State.QuoteInitialized
import com.r3.conclave.host.internal.AttestationHostHandler.State.ReportRetrieved
import com.r3.conclave.host.internal.attestation.EnclaveQuoteServiceFactory
import com.r3.conclave.host.internal.attestation.EnclaveQuoteService
import com.r3.conclave.utilities.internal.getRemainingBytes
import com.r3.conclave.utilities.internal.nullableSize
import com.r3.conclave.utilities.internal.putNullable
import java.nio.ByteBuffer

class AttestationHostHandler(
    private val attestationParameters: AttestationParameters?
) : Handler<AttestationHostHandler.Connection> {
    companion object {
        private val log = loggerFor<AttestationHostHandler>()
    }

    private sealed class State {
        object Ready : State()
        object QuoteInitialized : State()
        data class ReportRetrieved(val report: ByteCursor<SgxReport>) : State()
    }

    private val stateManager: StateManager<State> = StateManager(Ready)

    override fun onReceive(connection: Connection, input: ByteBuffer) {
        // The report object is used after this method returns, and so we must copy the bytes.
        val report = Cursor.wrap(SgxReport, input.getRemainingBytes())
        stateManager.transitionStateFrom<QuoteInitialized>(to = ReportRetrieved(report))
    }

    override fun connect(upstream: Sender): Connection = Connection(upstream)

    inner class Connection(private val upstream: Sender) {
        fun getSignedQuote(reportData: ByteCursor<SgxReportData>? = null): ByteCursor<SgxSignedQuote> {
            stateManager.checkStateIs<Ready>()
            return createSignedQuote(reportData)
        }

        private fun createSignedQuote(reportData: ByteCursor<SgxReportData>?): ByteCursor<SgxSignedQuote> {
            val attestationService = EnclaveQuoteServiceFactory.getService(attestationParameters)
            val quotingEnclaveTargetInfo = initializeQuote(attestationService)
            log.debug { "Quoting enclave's target info $quotingEnclaveTargetInfo" }
            val report = retrieveReport(quotingEnclaveTargetInfo, reportData)
            val signedQuote = retrieveQuote(attestationService, report)
            log.debug { "Got quote $signedQuote" }
            return signedQuote
        }

        private fun initializeQuote(enclaveQuoteService: EnclaveQuoteService): ByteCursor<SgxTargetInfo> {
            val targetInfo = enclaveQuoteService.initializeQuote()
            stateManager.transitionStateFrom<Ready>(to = QuoteInitialized)
            return targetInfo
        }

        private fun retrieveReport(quotingEnclaveTargetInfo: ByteCursor<SgxTargetInfo>, reportData: ByteCursor<SgxReportData>?): ByteCursor<SgxReport> {
            val size = quotingEnclaveTargetInfo.size + nullableSize(reportData) { reportData -> reportData.size }
            upstream.send(size) { buffer ->
                buffer.put(quotingEnclaveTargetInfo.buffer)
                buffer.putNullable(reportData) { reportData ->
                    buffer.put(reportData.buffer)
                }
            }
            val reportRetrieved = stateManager.checkStateIs<ReportRetrieved>()
            return reportRetrieved.report
        }

        private fun retrieveQuote(enclaveQuoteService: EnclaveQuoteService, report: ByteCursor<SgxReport>): ByteCursor<SgxSignedQuote> {
            val signedQuote = enclaveQuoteService.retrieveQuote(report)
            stateManager.transitionStateFrom<ReportRetrieved>(to = Ready)
            return signedQuote
        }
    }
}
