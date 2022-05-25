package com.r3.conclave.host.internal

import com.r3.conclave.common.internal.*
import com.r3.conclave.common.internal.handler.Handler
import com.r3.conclave.common.internal.handler.Sender
import com.r3.conclave.host.AttestationParameters
import com.r3.conclave.host.internal.AttestationHostHandler.State.QuoteInitialized
import com.r3.conclave.host.internal.AttestationHostHandler.State.ReportRetrieved
import com.r3.conclave.host.internal.attestation.EnclaveQuoteServiceFactory
import com.r3.conclave.host.internal.attestation.EnclaveQuoteService
import java.nio.ByteBuffer

class AttestationHostHandler(
    private val attestationParameters: AttestationParameters?
) : Handler<AttestationHostHandler.Connection> {
    companion object {
        private val log = loggerFor<AttestationHostHandler>()
    }

    private sealed class State {
        object Unstarted : State()
        object QuoteInitialized : State()
        data class ReportRetrieved(val report: ByteCursor<SgxReport>) : State()
        data class QuoteRetrieved(val signedQuote: ByteCursor<SgxSignedQuote>) : State()
    }

    private val stateManager: StateManager<State> = StateManager(State.Unstarted)

    override fun onReceive(connection: Connection, input: ByteBuffer) {
        // The report object is used after this method returns and so we must copy the bytes using Cursor.get.
        val report = Cursor.copy(SgxReport, input)
        stateManager.transitionStateFrom<QuoteInitialized>(to = ReportRetrieved(report))
    }

    override fun connect(upstream: Sender): Connection = Connection(upstream)

    inner class Connection(private val upstream: Sender) {
        fun getSignedQuote(ignoreCachedData: Boolean = false): ByteCursor<SgxSignedQuote> {
            val initialState = stateManager.state
            return when (initialState) {
                State.Unstarted -> createSignedQuote()
                is State.QuoteRetrieved -> if(ignoreCachedData) createSignedQuote() else getCachedSignedQuote(initialState)
                else -> throw IllegalStateException(initialState.toString())
            }
        }

        private fun createSignedQuote(): ByteCursor<SgxSignedQuote> {
            val attestationService = EnclaveQuoteServiceFactory.getService(attestationParameters)
            val quotingEnclaveTargetInfo = initializeQuote(attestationService)
            log.debug { "Quoting enclave's target info $quotingEnclaveTargetInfo" }
            val report = retrieveReport(quotingEnclaveTargetInfo)
            val signedQuote = retrieveQuote(attestationService, report)
            log.debug { "Got quote $signedQuote" }
            return signedQuote
        }

        private fun getCachedSignedQuote(state: State.QuoteRetrieved) =
            state.signedQuote

        private fun initializeQuote(enclaveQuoteService: EnclaveQuoteService): ByteCursor<SgxTargetInfo> {
            val targetInfo = enclaveQuoteService.initializeQuote()
            stateManager.state = QuoteInitialized
            return targetInfo
        }

        private fun retrieveReport(quotingEnclaveTargetInfo: ByteCursor<SgxTargetInfo>): ByteCursor<SgxReport> {
            upstream.send(quotingEnclaveTargetInfo.encoder.size) { buffer ->
                buffer.put(quotingEnclaveTargetInfo.buffer)
            }
            val reportRetrieved = stateManager.checkStateIs<ReportRetrieved>()
            return reportRetrieved.report
        }

        private fun retrieveQuote(enclaveQuoteService: EnclaveQuoteService, report: ByteCursor<SgxReport>): ByteCursor<SgxSignedQuote> {
            val signedQuote = enclaveQuoteService.retrieveQuote(report)
            stateManager.state = State.QuoteRetrieved(signedQuote)
            return signedQuote
        }
    }
}
