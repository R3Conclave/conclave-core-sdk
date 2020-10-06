package com.r3.conclave.host.internal

import com.r3.conclave.common.internal.*
import com.r3.conclave.common.internal.handler.Handler
import com.r3.conclave.common.internal.handler.Sender
import java.nio.ByteBuffer

// TODO Merge this into EpidAttestationHostHandler
class DCAPAttestationHostHandler(
        private val isMock: Boolean = false
) : Handler<DCAPAttestationHostHandler.Connection> {
    companion object {
        private val log = loggerFor<DCAPAttestationHostHandler>()
    }

    private sealed class State {
        object Unstarted : State()
        data class QuoteInitialized(val targetInfo: ByteCursor<SgxTargetInfo>) : State()
        data class ReportRetrieved(val report: ByteCursor<SgxReport>) : State()
        data class QuoteRetrieved(val signedQuote: ByteCursor<SgxSignedQuote>) : State()
    }

    private val stateManager: StateManager<State> = StateManager(State.Unstarted)

    override fun onReceive(connection: Connection, input: ByteBuffer) {
        stateManager.checkStateIs<State.QuoteInitialized>()
        stateManager.state = State.ReportRetrieved(Cursor.read(SgxReport, input))
    }

    override fun connect(upstream: Sender): Connection = Connection(upstream)

    inner class Connection(private val upstream: Sender) : AttestationHandlerConnection {
        override fun getSignedQuote(): ByteCursor<SgxSignedQuote> {
            val initialState = stateManager.state
            return when (initialState) {
                State.Unstarted -> {
                    val quotingEnclaveTargetInfo = initializeQuote() // sgx_get_target_info
                    val report = retrieveReport(quotingEnclaveTargetInfo) // send over to enclave, get sgx_report back (see onReceive)
                    val signedQuote = retrieveQuote(report) // get_quote_size + get_quote
                    signedQuote
                }
                is State.QuoteRetrieved -> initialState.signedQuote
                else -> throw IllegalStateException(initialState.toString())
            }
        }

        private fun initializeQuote(): ByteCursor<SgxTargetInfo> {
            val quoteResponse = Cursor.allocate(SgxTargetInfo)
            if (isMock) {
                log.debug("Mock initializeQuote")
            } else {
                Native.initQuoteDCAP(quoteResponse.buffer.array())
            }
            stateManager.state = State.QuoteInitialized(quoteResponse)
            return quoteResponse
        }

        private fun retrieveReport(quotingEnclaveTargetInfo: ByteCursor<SgxTargetInfo>): ByteCursor<SgxReport> {
            upstream.send(quotingEnclaveTargetInfo.encoder.size) { buffer ->
                buffer.put(quotingEnclaveTargetInfo.buffer)
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
                Native.calcQuoteSizeDCAP()
            }
            val signedQuote = Cursor.allocate(SgxSignedQuote(quoteSize))
            if (isMock) {
                // We can populate the other fields as needed, but for now we just need to copy over the report body.
                signedQuote.quote[SgxQuote.reportBody] = report[SgxReport.body].read()
            } else {
                val getQuote = Cursor.allocate(SgxGetQuote)
                getQuote[SgxGetQuote.report] = report.read()
                Native.getQuoteDCAP(
                        getQuote.buffer.array(),
                        signedQuote.buffer.array()
                )
            }
            stateManager.state = State.QuoteRetrieved(signedQuote)
            return signedQuote
        }
    }

}

