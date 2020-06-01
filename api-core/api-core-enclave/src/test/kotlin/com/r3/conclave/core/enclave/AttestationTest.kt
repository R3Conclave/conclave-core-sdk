package com.r3.conclave.core.enclave

import com.r3.conclave.common.internal.*
import com.r3.conclave.core.common.Handler
import com.r3.conclave.core.common.HandlerConnected
import com.r3.conclave.core.common.Sender
import com.r3.conclave.core.host.*
import com.r3.conclave.core.host.internal.Native
import com.r3.conclave.dynamictesting.EnclaveBuilder
import com.r3.conclave.dynamictesting.TestEnclaves
import com.r3.conclave.enclave.Enclave
import com.r3.conclave.enclave.internal.InternalEnclave
import com.r3.conclave.testing.BytesRecordingHandler
import org.junit.Before
import org.junit.ClassRule
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.CharBuffer
import java.util.function.Consumer
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AttestationTest {
    companion object {
        @JvmField
        @ClassRule
        val testEnclaves = TestEnclaves()
    }

    private lateinit var enclaveHandle: EnclaveHandle<*>

    @Before
    fun cleanUp() {
        if (this::enclaveHandle.isInitialized) {
            enclaveHandle.destroy()
        }
    }

    class ReportCreatingEnclave : InternalEnclave, Enclave() {
        override fun internalInitialise(api: EnclaveApi, upstream: Sender): HandlerConnected<*> {
            return HandlerConnected.connect(ReportCreatingHandler(api), upstream)
        }

        private class ReportCreatingHandler(private val api: EnclaveApi) : Handler<Sender> {
            override fun connect(upstream: Sender): Sender = upstream

            override fun onReceive(connection: Sender, input: ByteBuffer) {
                val targetInfo = input.getBytes(SgxTargetInfo.size())
                val reportData = input.getBytes(SgxReportData.size())

                val reportBytes = ByteArray(SgxReport.size())
                api.createReport(targetInfo, reportData, reportBytes)

                connection.send(reportBytes.size, Consumer { buffer ->
                    buffer.put(reportBytes)
                })
            }
        }
    }

    @Test
    fun `create report`() {
        val handler = BytesRecordingHandler()
        val connection = createEnclave(handler, ReportCreatingEnclave::class.java)
        val inputBuffer = ByteBuffer.allocate(SgxTargetInfo.size() + SgxReportData.size())
        inputBuffer.position(SgxTargetInfo.size())
        inputBuffer.put("hello".toByteArray())
        inputBuffer.position(0)

        connection.send(inputBuffer)

        val report = Cursor(SgxReport, handler.nextCall)
        val resultData = report[SgxReport.body][SgxReportBody.reportData]
        inputBuffer.position(SgxTargetInfo.size())
        println(Cursor(SgxReportData, inputBuffer))
        println(resultData)
        println(report)
        assertEquals(inputBuffer, resultData.read())
    }

    @Test
    fun `get quote`() {
        val handler = BytesRecordingHandler()
        val connection = createEnclave(handler, ReportCreatingEnclave::class.java)

        // 1. get the quoting enclave's measurement and the EPID group id
        val initQuoteResponse = Cursor.allocate(SgxInitQuoteResponse)
        Native.initQuote(initQuoteResponse.getBuffer().array())
        println("initQuote: $initQuoteResponse")

        // 2. get our enclave's report
        val inputData = Cursor.allocate(SgxReportData)
        inputData.getBuffer().put("hello".toByteArray())
        val inputBuffer = ByteBuffer.allocate(SgxTargetInfo.size() + SgxReportData.size())
        inputBuffer.put(initQuoteResponse[SgxInitQuoteResponse.quotingEnclaveTargetInfo].read())
        inputBuffer.put("hello".toByteArray())
        inputBuffer.position(0)
        connection.send(inputBuffer)

        val report = Cursor(SgxReport, handler.nextCall)
        val resultData = report[SgxReport.body][SgxReportBody.reportData]
        inputBuffer.position(SgxTargetInfo.size())
        println("report: $report")
        assertEquals(inputBuffer, resultData.read())

        // 3. get quote size
        val quoteSize = Native.calcQuoteSize(null)

        // 4. get quote
        val quoteRequest = Cursor.allocate(SgxGetQuote)
        quoteRequest[SgxGetQuote.report] = report.read()
        quoteRequest[SgxGetQuote.quoteType] = SgxQuoteType32.LINKABLE
        quoteRequest[SgxGetQuote.spid] = Cursor.allocate(SgxSpid).read()

        val signedQuote = Cursor.allocate(SgxSignedQuote(quoteSize))
        Native.getQuote(
                getQuoteRequestIn = quoteRequest.getBuffer().array(),
                signatureRevocationListIn = null,
                quotingEnclaveReportNonceIn = null,
                quotingEnclaveReportOut = null,
                quoteOut = signedQuote.getBuffer().array()
        )
        println("quote: $signedQuote")
        assertEquals(report[SgxReport.body], signedQuote.quote[SgxQuote.reportBody])
    }

    class TestEpidAttestationEnclaveHandler(api: EnclaveApi, reportDataString: String) : EpidAttestationEnclaveHandler(api) {
        override val reportData: ByteCursor<SgxReportData> by lazy {
            val reportData = Cursor.allocate(SgxReportData)
            val encoder = Charsets.UTF_8.newEncoder()
            encoder.encode(CharBuffer.wrap(reportDataString), reportData.getBuffer(), true)
            reportData
        }
    }

    class EpidAttestingEnclave : InternalEnclave, Enclave() {
        override fun internalInitialise(api: EnclaveApi, upstream: Sender): HandlerConnected<*> {
            return HandlerConnected.connect(TestEpidAttestationEnclaveHandler(api, "hello"), upstream)
        }
    }

    @Test
    fun `attestation handlers`() {
        val configuration = EpidAttestationHostConfiguration(
                quoteType = SgxQuoteType.LINKABLE,
                spid = Cursor.allocate(SgxSpid)
        )
        val connection = createEnclave(EpidAttestationHostHandler(configuration), EpidAttestingEnclave::class.java)
        val signedQuote = connection.getSignedQuote()
        val reportData = signedQuote.quote[SgxQuote.reportBody][SgxReportBody.reportData]
        assertTrue(Charsets.UTF_8.decode(reportData.getBuffer()).startsWith("hello"))
    }

    private fun <CONNECTION> createEnclave(
            handler: Handler<CONNECTION>,
            enclaveClass: Class<out Enclave>,
            enclaveBuilder: EnclaveBuilder = EnclaveBuilder()
    ): CONNECTION {
        val enclaveFile = testEnclaves.getSignedEnclaveFile(enclaveClass, enclaveBuilder)
        return NativeHostApi(EnclaveLoadMode.SIMULATION).createEnclave(handler, enclaveFile, enclaveClass.name).let {
            enclaveHandle = it
            it.connection
        }
    }
}
