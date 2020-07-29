package com.r3.conclave.host.internal

import com.r3.conclave.common.internal.*
import com.r3.conclave.common.internal.handler.Handler
import com.r3.conclave.common.internal.handler.Sender
import java.nio.ByteBuffer
import java.util.function.Consumer

class EpidAttestationHostHandler(
        private val quoteType: SgxQuoteType,
        private val spid: ByteCursor<SgxSpid>,
        private val isMock: Boolean = false
) : Handler<EpidAttestationHostHandler.Connection> {
    companion object {
        private val log = loggerFor<EpidAttestationHostHandler>()
    }

    private sealed class State {
        object Unstarted : State()
        data class QuoteInitialized(val initQuoteResponse: ByteCursor<SgxInitQuoteResponse>) : State()
        data class ReportRetrieved(val report: ByteCursor<SgxReport>) : State()
        data class QuoteRetrieved(val signedQuote: ByteCursor<SgxSignedQuote>) : State()
    }

    private val stateManager: StateManager<State> = StateManager(State.Unstarted)

    override fun onReceive(connection: Connection, input: ByteBuffer) {
        stateManager.checkStateIs<State.QuoteInitialized>()
        stateManager.state = State.ReportRetrieved(Cursor.read(SgxReport, input))
    }

    override fun connect(upstream: Sender): Connection = Connection(upstream)

    inner class Connection(private val upstream: Sender) {
        fun getSignedQuote(): ByteCursor<SgxSignedQuote> {
            val initialState = stateManager.state
            return when (initialState) {
                State.Unstarted -> {
                    val quotingEnclaveTargetInfo = initializeQuote()
                    log.debug { "Quoting enclave's target info $quotingEnclaveTargetInfo" }
                    val report = retrieveReport(quotingEnclaveTargetInfo)
                    val signedQuote = retrieveQuote(report)
                    log.debug { "Got quote $signedQuote" }
                    signedQuote
                }
                is State.QuoteRetrieved -> initialState.signedQuote
                else -> throw IllegalStateException(initialState.toString())
            }
        }

        private fun initializeQuote(): ByteCursor<SgxTargetInfo> {
            val quoteResponse = Cursor.allocate(SgxInitQuoteResponse)
            if (isMock) {
                log.debug("Mock initializeQuote")
            } else {
                Native.initQuote(quoteResponse.buffer.array())
            }
            stateManager.state = State.QuoteInitialized(quoteResponse)
            return quoteResponse[SgxInitQuoteResponse.quotingEnclaveTargetInfo]
        }

        private fun retrieveReport(quotingEnclaveTargetInfo: ByteCursor<SgxTargetInfo>): ByteCursor<SgxReport> {
            quotingEnclaveTargetInfo.read().let {
                upstream.send(it.remaining(), Consumer { buffer ->
                    buffer.put(it)
                })
            }
            val reportRetrieved = stateManager.checkStateIs<State.ReportRetrieved>()
            return reportRetrieved.report
        }

        private fun retrieveQuote(report: ByteCursor<SgxReport>): ByteCursor<SgxSignedQuote> {
            val quoteSize = if (isMock) {
                // SgxSignedQuote
                // The 256 is an arbitrary signature size
                SgxQuote.size + Int32().size + 256
            } else {
                Native.calcQuoteSize(null)
            }
            val signedQuote = Cursor.allocate(SgxSignedQuote(quoteSize))
            if (isMock) {
                // We can populate the other fields as needed, but for now we just need to copy over the report body.
                signedQuote.quote[SgxQuote.reportBody] = report[SgxReport.body].read()
            } else {
                val getQuote = Cursor.allocate(SgxGetQuote)
                getQuote[SgxGetQuote.report] = report.read()
                getQuote[SgxGetQuote.quoteType] = quoteType.value
                getQuote[SgxGetQuote.spid] = spid.read()
                Native.getQuote(
                        getQuote.buffer.array(),
                        signatureRevocationListIn = null,
                        // TODO Do we need to use the nonce?
                        quotingEnclaveReportNonceIn = null,
                        quotingEnclaveReportOut = null,
                        quoteOut = signedQuote.buffer.array()
                )
            }
            stateManager.state = State.QuoteRetrieved(signedQuote)
            return signedQuote
        }
    }

}
