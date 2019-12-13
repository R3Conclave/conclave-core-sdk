import com.r3.sgx.core.common.*
import com.r3.sgx.core.enclave.EnclaveApi
import com.r3.sgx.core.enclave.EpidAttestationEnclaveHandler
import com.r3.sgx.core.enclave.RootEnclave
import com.r3.sgx.core.host.EpidAttestationHostConfiguration
import com.r3.sgx.core.host.EpidAttestationHostHandler
import com.r3.sgx.core.host.internal.Native
import com.r3.sgx.testing.BytesEnclave
import com.r3.sgx.testing.BytesRecordingHandler
import com.r3.sgx.dynamictesting.TestEnclavesBasedTest
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.CharBuffer
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AttestationTest : TestEnclavesBasedTest() {

    class ReportCreatingEnclave : BytesEnclave() {
        override fun onReceive(api: EnclaveApi, connection: BytesHandler.Connection, bytes: ByteBuffer) {
            val targetInfo = ByteArray(SgxTargetInfo.size())
            bytes.get(targetInfo)

            val reportData = ByteArray(SgxReportData.size())
            bytes.get(reportData)

            val reportBytes = Cursor.allocate(SgxReport)
            api.createReport(targetInfo, reportData, reportBytes.getBuffer().array())
            connection.send(reportBytes.getBuffer())
        }
    }

    @Test
    fun canCreateReport() {
        val handler = BytesRecordingHandler()
        val connection = createEnclave(ReportCreatingEnclave::class.java).addDownstream(handler)
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
    fun canGetQuote() {
        val handler = BytesRecordingHandler()
        val connection = createEnclave(ReportCreatingEnclave::class.java).addDownstream(handler)

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

        val sgxQuote = SgxSignedQuote(quoteSize)
        val quote = Cursor.allocate(sgxQuote)
        Native.getQuote(
                getQuoteRequestIn = quoteRequest.getBuffer().array(),
                signatureRevocationListIn = null,
                quotingEnclaveReportNonceIn = null,
                quotingEnclaveReportOut = null,
                quoteOut = quote.getBuffer().array()
        )
        println("quote: $quote")
        assertEquals(report[SgxReport.body], quote[sgxQuote.quote][SgxQuote.reportBody])
    }

    class TestEpidAttestationEnclaveHandler(api: EnclaveApi, reportDataString: String) : EpidAttestationEnclaveHandler(api) {
        override val reportData: Cursor<ByteBuffer, SgxReportData> by lazy {
            val reportData = Cursor.allocate(SgxReportData)
            val encoder = Charsets.UTF_8.newEncoder()
            encoder.encode(CharBuffer.wrap(reportDataString), reportData.getBuffer(), true)
            reportData
        }
    }

    class EpidAttestingEnclave : RootEnclave() {
        override fun initialize(api: EnclaveApi, mux: SimpleMuxingHandler.Connection) {
            mux.addDownstream(TestEpidAttestationEnclaveHandler(api, "hello"))
        }
    }

    @Test
    fun attestationHandlersWork() {
        val configuration = EpidAttestationHostConfiguration(
                quoteType = SgxQuoteType32.LINKABLE,
                spid = Cursor.allocate(SgxSpid)
        )
        val connection = createEnclave(EpidAttestingEnclave::class.java).addDownstream(EpidAttestationHostHandler(configuration))
        val quote = connection.getQuote()
        val reportData = quote[quote.encoder.quote][SgxQuote.reportBody][SgxReportBody.reportData]
        assertTrue(Charsets.UTF_8.decode(reportData.getBuffer()).startsWith("hello"))
    }
}