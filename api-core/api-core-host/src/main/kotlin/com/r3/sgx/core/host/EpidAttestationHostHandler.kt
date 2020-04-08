package com.r3.sgx.core.host

import com.google.protobuf.ByteString
import com.r3.conclave.common.internal.*
import com.r3.sgx.core.common.*
import com.r3.sgx.core.host.internal.Native

data class EpidAttestationHostConfiguration(
        val quoteType: SgxQuoteType,
        val spid: ByteCursor<SgxSpid>
)

class EpidAttestationHostHandler(
        private val configuration: EpidAttestationHostConfiguration,
        private val isMock: Boolean = false
) : ProtoHandler<EpidEnclaveMessage, EpidHostMessage, EpidAttestationHostHandler.Connection>(EpidEnclaveMessage.parser()) {

    companion object {
        private val log = loggerFor<EpidAttestationHostHandler>()
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
        fun getSignedQuote(): ByteCursor<SgxSignedQuote> {
            val initialState = state
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
                log.info("Mock initializeQuote")
            } else {
                Native.initQuote(quoteResponse.getBuffer().array())
            }
            state = State.QuoteInitialized(quoteResponse)
            return quoteResponse[SgxInitQuoteResponse.quotingEnclaveTargetInfo]
        }

        private fun retrieveReport(quotingEnclaveTargetInfo: ByteCursor<SgxTargetInfo>): ByteCursor<SgxReport> {
            val getReport = EpidHostMessage.newBuilder()
                    .setGetReportRequest(
                            GetReportRequest.newBuilder()
                                    .setQuotingEnclaveTargetInfo(ByteString.copyFrom(quotingEnclaveTargetInfo.read()))
                    )
                    .build()
            upstream.send(getReport)
            val reportRetrieved = stateAs<State.ReportRetrieved>()
            return reportRetrieved.report
        }

        private fun retrieveQuote(report: ByteCursor<SgxReport>): ByteCursor<SgxSignedQuote> {
            val quoteSize = if (isMock) {
                // SgxSignedQuote
                // The 256 is an arbitrary signature size
                SgxQuote.size() + Int32().size() + 256
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
                getQuote[SgxGetQuote.quoteType] = configuration.quoteType.value
                getQuote[SgxGetQuote.spid] = configuration.spid.read()
                Native.getQuote(
                        getQuote.getBuffer().array(),
                        signatureRevocationListIn = null,
                        // TODO Do we need to use the nonce?
                        quotingEnclaveReportNonceIn = null,
                        quotingEnclaveReportOut = null,
                        quoteOut = signedQuote.getBuffer().array()
                )
            }
            state = State.QuoteRetrieved(signedQuote)
            return signedQuote
        }
    }

}
