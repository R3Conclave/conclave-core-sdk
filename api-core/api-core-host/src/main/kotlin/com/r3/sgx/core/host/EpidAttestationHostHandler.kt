package com.r3.sgx.core.host

import com.google.protobuf.ByteString
import com.r3.sgx.core.common.*
import com.r3.sgx.core.host.internal.Native
import org.slf4j.LoggerFactory
import java.nio.ByteBuffer

data class EpidAttestationHostConfiguration(
        val quoteType: Int,
        val spid: Cursor<ByteBuffer, SgxSpid>
)

class EpidAttestationHostHandler(
        val configuration: EpidAttestationHostConfiguration
) : ProtoHandler<EpidEnclaveMessage, EpidHostMessage, EpidAttestationHostHandler.Connection>(EpidEnclaveMessage.parser()) {

    companion object {
        private val log = LoggerFactory.getLogger(EpidAttestationHostHandler::class.java)
    }

    private sealed class State {
        object Unstarted : State()
        data class QuoteInitialized(
                val initQuoteResponse: Cursor<ByteBuffer, SgxInitQuoteResponse>
        ) : State()
        data class ReportRetrieved(
                val report: Cursor<ByteBuffer, SgxReport>
        ) : State()
        data class QuoteRetrieved(
                val signedQuote: Cursor<ByteBuffer, SgxSignedQuote>
        ) : State()
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
        fun getQuote(): Cursor<ByteBuffer, SgxSignedQuote> {
            val initialState = state
            return when (initialState) {
                EpidAttestationHostHandler.State.Unstarted -> {
                    log.info("Initializing quote")
                    val quotingEnclaveTargetInfo = initializeQuote()
                    log.info("Quoting enclave's target info $quotingEnclaveTargetInfo")
                    val report = retrieveReport(quotingEnclaveTargetInfo)
                    log.info("Got report $report")
                    val quote = retrieveQuote(report)
                    log.info("Got quote $quote")
                    quote
                }
                is EpidAttestationHostHandler.State.QuoteRetrieved -> {
                    initialState.signedQuote
                }
                else -> {
                    throw IllegalStateException()
                }
            }
        }

        private fun initializeQuote(): Cursor<ByteBuffer, SgxTargetInfo> {
            val quoteResponse = Cursor.allocate(SgxInitQuoteResponse)
            Native.initQuote(quoteResponse.getBuffer().array())
            state = State.QuoteInitialized(quoteResponse)
            return quoteResponse[SgxInitQuoteResponse.quotingEnclaveTargetInfo]
        }

        private fun retrieveReport(quotingEnclaveTargetInfo: Cursor<ByteBuffer, SgxTargetInfo>): Cursor<ByteBuffer, SgxReport> {
            val getReport = EpidHostMessage.newBuilder()
                    .setGetReportRequest(GetReportRequest.newBuilder()
                            .setQuotingEnclaveTargetInfo(ByteString.copyFrom(quotingEnclaveTargetInfo.read())))
                    .build()
            upstream.send(getReport)
            val reportRetrieved = stateAs<State.ReportRetrieved>()
            return reportRetrieved.report
        }

        private fun retrieveQuote(report: Cursor<ByteBuffer, SgxReport>): Cursor<ByteBuffer, SgxSignedQuote> {
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