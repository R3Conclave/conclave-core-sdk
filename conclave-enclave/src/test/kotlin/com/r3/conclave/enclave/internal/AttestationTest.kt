package com.r3.conclave.enclave.internal

import com.r3.conclave.common.internal.*
import com.r3.conclave.common.internal.handler.Handler
import com.r3.conclave.common.internal.handler.HandlerConnected
import com.r3.conclave.common.internal.handler.Sender
import com.r3.conclave.dynamictesting.TestEnclaves
import com.r3.conclave.enclave.Enclave
import com.r3.conclave.host.internal.EnclaveHandle
import com.r3.conclave.host.internal.EpidAttestationHostHandler
import com.r3.conclave.host.internal.Native
import com.r3.conclave.testing.BytesRecordingHandler
import com.r3.conclave.utilities.internal.getBytes
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import java.nio.ByteBuffer
import java.nio.CharBuffer
import java.util.function.Consumer

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
                val targetInfo = input.getBytes(SgxTargetInfo.size)
                val reportData = input.getBytes(SgxReportData.size)

                val reportBytes = ByteArray(SgxReport.size)
                env.createReport(targetInfo, reportData, reportBytes)

                connection.send(reportBytes.size, Consumer { buffer ->
                    buffer.put(reportBytes)
                })
            }
        }
    }

    @Test
    fun `create report`() {
        val connection = testEnclaves.createOrGetEnclave(
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
        val connection = testEnclaves.createOrGetEnclave(
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
        val quoteSize = Native.calcQuoteSize(null)

        // 4. get quote
        val quoteRequest = Cursor.allocate(SgxGetQuote)
        quoteRequest[SgxGetQuote.report] = report.read()
        quoteRequest[SgxGetQuote.quoteType] = SgxQuoteType32.LINKABLE
        quoteRequest[SgxGetQuote.spid] = Cursor.allocate(SgxSpid).read()

        val signedQuote = Cursor.allocate(SgxSignedQuote(quoteSize))
        Native.getQuote(
                getQuoteRequestIn = quoteRequest.buffer.array(),
                signatureRevocationListIn = null,
                quotingEnclaveReportNonceIn = null,
                quotingEnclaveReportOut = null,
                quoteOut = signedQuote.buffer.array()
        )
        println("quote: $signedQuote")
        assertEquals(report[SgxReport.body], signedQuote.quote[SgxQuote.reportBody])
    }

    class TestEpidAttestationEnclaveHandler(env: EnclaveEnvironment, reportDataString: String) : EpidAttestationEnclaveHandler(env) {
        override val reportData: ByteCursor<SgxReportData> by lazy {
            val reportData = Cursor.allocate(SgxReportData)
            val encoder = Charsets.UTF_8.newEncoder()
            encoder.encode(CharBuffer.wrap(reportDataString), reportData.buffer, true)
            reportData
        }
    }

    class EpidAttestingEnclave : InternalEnclave, Enclave() {
        override fun internalInitialise(env: EnclaveEnvironment, upstream: Sender): HandlerConnected<*> {
            return HandlerConnected.connect(TestEpidAttestationEnclaveHandler(env, "hello"), upstream)
        }
    }

    @Test
    fun `attestation handlers`() {
        val connection = testEnclaves.createOrGetEnclave(
                handler = EpidAttestationHostHandler(SgxQuoteType.LINKABLE, Cursor.allocate(SgxSpid)),
                enclaveClass = EpidAttestingEnclave::class.java
        )
        val signedQuote = connection.getSignedQuote()
        val reportData = signedQuote.quote[SgxQuote.reportBody][SgxReportBody.reportData]
        assertTrue(Charsets.UTF_8.decode(reportData.buffer).startsWith("hello"))
    }

}
