package com.r3.conclave.enclave.internal

import com.r3.conclave.common.OpaqueBytes
import com.r3.conclave.common.internal.*
import com.r3.conclave.common.internal.SgxSignedQuote.quote
import com.r3.conclave.common.internal.handler.Handler
import com.r3.conclave.common.internal.handler.HandlerConnected
import com.r3.conclave.common.internal.handler.Sender
import com.r3.conclave.enclave.Enclave
import com.r3.conclave.host.AttestationParameters
import com.r3.conclave.host.internal.AttestationHostHandler
import com.r3.conclave.host.internal.EnclaveHandle
import com.r3.conclave.host.internal.Native
import com.r3.conclave.internaltesting.BytesRecordingHandler
import com.r3.conclave.internaltesting.dynamic.TestEnclaves
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import java.nio.ByteBuffer
import java.nio.CharBuffer
import kotlin.random.Random

class AttestationTest {
    companion object {
        @JvmField
        @RegisterExtension
        val testEnclaves = TestEnclaves()

        val bytesRecordingHandler = BytesRecordingHandler()

        @AfterAll
        @JvmStatic
        internal fun afterAll() {
            EnclaveRecycler.clear()
        }
    }

    private lateinit var enclaveHandle: EnclaveHandle<*>

    @BeforeEach
    fun cleanUp() {
        if (this::enclaveHandle.isInitialized) {
            enclaveHandle.destroy()
        }
    }

    class ReportCreatingEnclave : InternalEnclave, Enclave() {
        override fun internalInitialise(env: EnclaveEnvironment, upstream: Sender): HandlerConnected<*> {
            return HandlerConnected.connect(ReportCreatingHandler(env), upstream)
        }

        private class ReportCreatingHandler(private val env: EnclaveEnvironment) : Handler<Sender> {
            override fun connect(upstream: Sender): Sender = upstream

            override fun onReceive(connection: Sender, input: ByteBuffer) {
                val targetInfo = Cursor.read(SgxTargetInfo, input)
                val reportData = Cursor.read(SgxReportData, input)
                val report = env.createReport(targetInfo, reportData)
                connection.send(report.encoder.size) { buffer ->
                    buffer.put(report.buffer)
                }
            }
        }
    }

    @Test
    fun `create report`() {
        val connection = testEnclaves.createOrGetEnclaveConnection(
                handler = bytesRecordingHandler,
                enclaveClass = ReportCreatingEnclave::class.java
        )
        val inputBuffer = ByteBuffer.allocate(SgxTargetInfo.size + SgxReportData.size)
        inputBuffer.position(SgxTargetInfo.size)
        inputBuffer.put("hello".toByteArray())
        inputBuffer.position(0)

        connection.send(inputBuffer)

        val report = Cursor.read(SgxReport, bytesRecordingHandler.nextCall)
        val resultData = report[SgxReport.body][SgxReportBody.reportData]
        inputBuffer.position(SgxTargetInfo.size)
        assertEquals(inputBuffer, resultData.read())
    }

    @Test
    fun `get quote`() {
        val connection = testEnclaves.createOrGetEnclaveConnection(
                handler = bytesRecordingHandler,
                enclaveClass = ReportCreatingEnclave::class.java
        )

        // 1. get the quoting enclave's measurement and the EPID group id
        val initQuoteResponse = Cursor.allocate(SgxInitQuoteResponse)
        Native.initQuote(initQuoteResponse.buffer.array())
        println("initQuote: $initQuoteResponse")

        // 2. get our enclave's report
        val inputData = Cursor.allocate(SgxReportData)
        inputData.buffer.put("hello".toByteArray())
        val inputBuffer = ByteBuffer.allocate(SgxTargetInfo.size + SgxReportData.size)
        inputBuffer.put(initQuoteResponse[SgxInitQuoteResponse.quotingEnclaveTargetInfo].read())
        inputBuffer.put("hello".toByteArray())
        inputBuffer.position(0)
        connection.send(inputBuffer)

        val report = Cursor.read(SgxReport, bytesRecordingHandler.nextCall)
        val resultData = report[SgxReport.body][SgxReportBody.reportData]
        inputBuffer.position(SgxTargetInfo.size)
        println("report: $report")
        assertEquals(inputBuffer, resultData.read())

        // 3. get quote size
        val quoteBytes = ByteArray(Native.calcQuoteSize(null))

        // 4. get quote
        val quoteRequest = Cursor.allocate(SgxGetQuote)
        quoteRequest[SgxGetQuote.report] = report.read()
        quoteRequest[SgxGetQuote.quoteType] = SgxQuoteType32.LINKABLE
        quoteRequest[SgxGetQuote.spid] = Cursor.allocate(SgxSpid).read()

        Native.getQuote(
                getQuoteRequestIn = quoteRequest.buffer.array(),
                signatureRevocationListIn = null,
                quotingEnclaveReportNonceIn = null,
                quotingEnclaveReportOut = null,
                quoteOut = quoteBytes
        )
        val signedQuote = Cursor.wrap(SgxSignedQuote, quoteBytes)
        println("quote: $signedQuote")
        assertEquals(report[SgxReport.body], signedQuote[quote][SgxQuote.reportBody])
    }

    class TestAttestationEnclaveHandler(env: EnclaveEnvironment, reportDataString: String) : AttestationEnclaveHandler(env) {
        override val reportData: ByteCursor<SgxReportData> by lazy {
            val reportData = Cursor.allocate(SgxReportData)
            val encoder = Charsets.UTF_8.newEncoder()
            encoder.encode(CharBuffer.wrap(reportDataString), reportData.buffer, true)
            reportData
        }
    }

    class AttestingEnclave : InternalEnclave, Enclave() {
        override fun internalInitialise(env: EnclaveEnvironment, upstream: Sender): HandlerConnected<*> {
            return HandlerConnected.connect(TestAttestationEnclaveHandler(env, "hello"), upstream)
        }
    }

    @Test
    fun `attestation handlers`() {
        val connection = testEnclaves.createOrGetEnclaveConnection(
                handler = AttestationHostHandler(AttestationParameters.EPID(OpaqueBytes(Random.nextBytes(16)), "")),
                enclaveClass = AttestingEnclave::class.java
        )
        val signedQuote = connection.getSignedQuote()
        val reportData = signedQuote[quote][SgxQuote.reportBody][SgxReportBody.reportData]
        assertTrue(Charsets.UTF_8.decode(reportData.buffer).startsWith("hello"))
    }
}
