package com.r3.conclave.host.internal

import com.r3.conclave.common.internal.*
import com.r3.conclave.common.internal.SgxInitQuoteResponse.quotingEnclaveTargetInfo
import com.r3.conclave.common.internal.SgxSignedQuote.quote
import com.r3.conclave.common.internal.handler.Handler
import com.r3.conclave.common.internal.handler.Sender
import com.r3.conclave.host.AttestationParameters
import com.r3.conclave.host.internal.AttestationHostHandler.State.QuoteInitialized
import com.r3.conclave.host.internal.AttestationHostHandler.State.ReportRetrieved
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
        val report = Cursor.read(SgxReport, input)
        stateManager.transitionStateFrom<QuoteInitialized>(to = ReportRetrieved(report))
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
            val targetInfo = when (attestationParameters) {
                is AttestationParameters.EPID -> {
                    val quoteResponse = Cursor.allocate(SgxInitQuoteResponse)
                    Native.initQuote(quoteResponse.buffer.array())
                    quoteResponse[quotingEnclaveTargetInfo]
                }
                is AttestationParameters.DCAP -> {
                    val targetInfo = Cursor.allocate(SgxTargetInfo)
                    Native.initQuoteDCAP(NativeLoader.libsPath.toString(), targetInfo.buffer.array())
                    targetInfo
                }
                null -> {
                    log.debug("Mock initializeQuote")
                    Cursor.allocate(SgxTargetInfo)
                }
            }
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

        private fun retrieveQuote(report: ByteCursor<SgxReport>): ByteCursor<SgxSignedQuote> {
            val signedQuote = when (attestationParameters) {
                is AttestationParameters.EPID -> {
                    val quoteBytes = ByteArray(Native.calcQuoteSize(null))
                    val getQuote = Cursor.allocate(SgxGetQuote)
                    getQuote[SgxGetQuote.report] = report.read()
                    getQuote[SgxGetQuote.quoteType] = SgxQuoteType.LINKABLE.value
                    getQuote[SgxGetQuote.spid] = attestationParameters.spid.buffer()
                    Native.getQuote(
                        getQuote.buffer.array(),
                        signatureRevocationListIn = null,
                        // TODO Do we need to use the nonce?
                        quotingEnclaveReportNonceIn = null,
                        quotingEnclaveReportOut = null,
                        quoteOut = quoteBytes
                    )
                    Cursor.wrap(SgxSignedQuote, quoteBytes)
                }
                is AttestationParameters.DCAP -> {
                    val quoteBytes = ByteArray(Native.calcQuoteSizeDCAP())
                    val getQuote = Cursor.allocate(SgxGetQuote)
                    getQuote[SgxGetQuote.report] = report.read()
                    Native.getQuoteDCAP(getQuote.buffer.array(), quoteBytes)
                    Cursor.wrap(SgxSignedQuote, quoteBytes)
                }
                null -> {
                    val signedQuote = Cursor.wrap(SgxSignedQuote, ByteArray(SgxSignedQuote.minSize))
                    // We can populate the other fields as needed, but for now we just need to copy over the report body.
                    signedQuote[quote][SgxQuote.reportBody] = report[SgxReport.body].read()
                    signedQuote
                }
            }
            stateManager.state = State.QuoteRetrieved(signedQuote)
            return signedQuote
        }
    }
}
