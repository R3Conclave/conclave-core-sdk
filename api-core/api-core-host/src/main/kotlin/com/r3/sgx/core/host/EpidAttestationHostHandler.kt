package com.r3.sgx.core.host

import com.google.protobuf.ByteString
import com.r3.sgx.core.common.*
import com.r3.sgx.core.host.internal.Native
import org.slf4j.LoggerFactory

data class EpidAttestationHostConfiguration(
        val quoteType: Int,
        val spid: ByteCursor<SgxSpid>
)

class EpidAttestationHostHandler(
        val configuration: EpidAttestationHostConfiguration
) : ProtoHandler<EpidEnclaveMessage, EpidHostMessage, EpidAttestationHostHandler.Connection>(EpidEnclaveMessage.parser()) {

    companion object {
        private val log = LoggerFactory.getLogger(EpidAttestationHostHandler::class.java)
    }

    private sealed class State {
        object Unstarted : State()
        data class QuoteInitialized(val initQuoteResponse: ByteCursor<SgxInitQuoteResponse>) : State()
        data class ReportRetrieved(val report: ByteCursor<SgxReport>) : State()
        data class QuoteRetrieved(val signedQuote: ByteCursor<SgxSignedQuote>) : State()
    }

    private var state: State = State.Unstarted
    private inline fun <reified S: State> stateAs(): S {
        val localState = state
        if (localState is S) {
            return localState
        } else {
            throw IllegalStateException("Expected state to be ${S::class.java.simpleName}, but was ${localState.javaClass.simpleName}")
        }
    }

    override fun onReceive(connection: Connection, message: EpidEnclaveMessage) {
        when {
            message.hasGetReportReply() -> {
                stateAs<State.QuoteInitialized>()
                val report = message.getReportReply.report.asReadOnlyByteBuffer()
                state = State.ReportRetrieved(Cursor(SgxReport, report))
            }
            else -> {
                throw IllegalStateException()
            }
        }
    }

    override fun connect(upstream: ProtoSender<EpidHostMessage>): Connection {
        return Connection(upstream)
    }

    inner class Connection(val upstream: ProtoSender<EpidHostMessage>) {
        fun getQuote(): ByteCursor<SgxSignedQuote> {
            val initialState = state
            return when (initialState) {
                State.Unstarted -> {
                    log.info("Initializing quote")
                    val quotingEnclaveTargetInfo = initializeQuote()
                    log.info("Quoting enclave's target info $quotingEnclaveTargetInfo")
                    val report = retrieveReport(quotingEnclaveTargetInfo)
                    log.info("Got report $report")
                    val quote = retrieveQuote(report)
                    log.info("Got quote $quote")
                    quote
                }
                is State.QuoteRetrieved -> {
                    initialState.signedQuote
                }
                else -> {
                    throw IllegalStateException()
                }
            }
        }

        private fun initializeQuote(): ByteCursor<SgxTargetInfo> {
            val quoteResponse = Cursor.allocate(SgxInitQuoteResponse)
            Native.initQuote(quoteResponse.getBuffer().array())
            state = State.QuoteInitialized(quoteResponse)
            return quoteResponse[SgxInitQuoteResponse.quotingEnclaveTargetInfo]
        }

        private fun retrieveReport(quotingEnclaveTargetInfo: ByteCursor<SgxTargetInfo>): ByteCursor<SgxReport> {
            val getReport = EpidHostMessage.newBuilder()
                    .setGetReportRequest(GetReportRequest.newBuilder()
                            .setQuotingEnclaveTargetInfo(ByteString.copyFrom(quotingEnclaveTargetInfo.read())))
                    .build()
            upstream.send(getReport)
            val reportRetrieved = stateAs<State.ReportRetrieved>()
            return reportRetrieved.report
        }

        private fun retrieveQuote(report: ByteCursor<SgxReport>): ByteCursor<SgxSignedQuote> {
            val quoteSize = Native.calcQuoteSize(null)
            val getQuoteRequest = Cursor.allocate(SgxGetQuote)
            getQuoteRequest[SgxGetQuote.report] = report.read()
            getQuoteRequest[SgxGetQuote.quoteType] = configuration.quoteType
            getQuoteRequest[SgxGetQuote.spid] = configuration.spid.read()
            val sgxQuote = SgxSignedQuote(quoteSize)
            val quote = Cursor.allocate(sgxQuote)
            Native.getQuote(
                    getQuoteRequest.getBuffer().array(),
                    signatureRevocationListIn = null,
                    quotingEnclaveReportNonceIn = null,
                    quotingEnclaveReportOut = null,
                    quoteOut = quote.getBuffer().array()
            )
            state = State.QuoteRetrieved(quote)
            return quote
        }
    }

}