import com.r3.sgx.core.common.ChannelHandlingHandler
import com.r3.sgx.core.common.ChannelInitiatingHandler
import com.r3.sgx.core.common.MuxId
import com.r3.sgx.core.common.SimpleMuxingHandler
import com.r3.sgx.core.enclave.EnclaveApi
import com.r3.sgx.core.enclave.RootEnclave
import com.r3.sgx.dynamictesting.EnclaveBuilder
import com.r3.sgx.dynamictesting.EnclaveConfig
import com.r3.sgx.dynamictesting.TestEnclavesBasedTest
import com.r3.sgx.testing.StringHandler
import com.r3.sgx.testing.StringRecordingHandler
import com.r3.sgx.testing.StringSender
import org.junit.Test
import org.slf4j.LoggerFactory
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import kotlin.collections.set
import kotlin.test.assertEquals

class ChannelHandlerTest : TestEnclavesBasedTest() {

    companion object {
        private val log = LoggerFactory.getLogger(ChannelHandlerTest::class.java)
    }

    class AddingHandler : StringHandler() {
        private val sum = AtomicLong(0)
        override fun onReceive(sender: StringSender, string: String) {
            when (string) {
                "GET_SUM" -> {
                    sender.send(sum.get().toString())
                }
                else -> {
                    sum.addAndGet(java.lang.Long.parseLong(string))
                }
            }
        }
    }

    class ChannelsSizeReportingHandler(private val channels: ChannelHandlingHandler.Connection) : StringHandler() {
        override fun onReceive(sender: StringSender, string: String) {
            sender.send(channels.getChannelIds().size.toString())
        }
    }

    class ChannelAddingEnclave : RootEnclave() {
        override fun initialize(api: EnclaveApi, mux: SimpleMuxingHandler.Connection) {
            val channels = mux.addDownstream(object : ChannelHandlingHandler() {
                override fun createHandler() = AddingHandler()
            })
            mux.addDownstream(ChannelsSizeReportingHandler(channels))
        }
    }

    @Test
    fun openSendCloseWorks() {
        val addingHandler = StringRecordingHandler()
        val reportingHandler = StringRecordingHandler()
        val root = createEnclave(ChannelAddingEnclave::class.java)
        val channels = root.addDownstream(ChannelInitiatingHandler())
        val reportingSender = root.addDownstream(reportingHandler)
        val (muxId, addingSender) = channels.addDownstream(addingHandler).get()

        // We send 1 number
        addingSender.send(100.toString())
        addingSender.send("GET_SUM")
        assertEquals(1, addingHandler.calls.size)
        assertEquals("100", addingHandler.calls[0])
        assertEquals(1, channels.getChannelIds().size)

        // Check number of channels in enclave
        reportingSender.send("")
        assertEquals(1, reportingHandler.calls.size)
        assertEquals("1", reportingHandler.calls[0])

        // Remove channel
        channels.removeDownstream(muxId)
        assertEquals(0, channels.getChannelIds().size)

        // Check number of channels in enclave
        reportingSender.send("")
        assertEquals(2, reportingHandler.calls.size)
        assertEquals("0", reportingHandler.calls[1])
    }

    @Test
    fun parallelChannelsWork() {
        val addingHandler = StringRecordingHandler()
        val reportingHandler = StringRecordingHandler()
        val enclaveBuilder = EnclaveBuilder().withConfig(EnclaveConfig().withTCSNum(20))
        val root = createEnclave(ChannelAddingEnclave::class.java, enclaveBuilder)
        val channels = root.addDownstream(ChannelInitiatingHandler())
        val reportingSender = root.addDownstream(reportingHandler)

        val muxIds = Collections.synchronizedList(ArrayList<MuxId>())
        val senders = ConcurrentHashMap<MuxId, StringSender>()
        val N = 10000L
        val random = Random()
        log.info("Sending $N numbers for summing")
        (1 .. N).toList().parallelStream().forEach { number ->
            // We create a new channel if the generated number == 0. The number of channels created will be ~ log(N)
            val i = random.nextInt(muxIds.size + 1)
            if (i == 0) {
                val (newMuxId, addingSender) = channels.addDownstream(addingHandler).get()
                senders[newMuxId] = addingSender
                addingSender.send(number.toString())
                muxIds.add(newMuxId)
                //events.add(newMuxId to number)
            } else {
                val muxId = muxIds[i - 1]
                senders[muxId]!!.send(number.toString())
                //events.add(muxId to number)
            }
        }
        assertEquals(senders.keys, channels.getChannelIds().toSet())

        // We created as many channels in the enclave as outside
        reportingSender.send("")
        assertEquals(1, reportingHandler.calls.size)
        assertEquals(senders.size, channels.getChannelIds().size)
        assertEquals(senders.size.toString(), reportingHandler.calls[0])
        log.info("${senders.size} channels created")

        log.info("Getting each channel's sum")
        // Get each channel's sum
        for (muxId in 0 until muxIds.size) {
            senders[muxId]!!.send("GET_SUM")
        }

        // Every channel reported
        assertEquals(senders.size, addingHandler.calls.size)
        val overallSum = addingHandler.calls.stream().mapToLong(java.lang.Long::parseLong).sum()
        assertEquals(N * (N + 1) / 2, overallSum)
        log.info("Overall sum $overallSum")

        log.info("Closing all channels")
        // Close all channels in parallel
        senders.keys.parallelStream().forEach { muxId ->
            channels.removeDownstream(muxId)
        }
        log.info("Closed ${senders.keys.size} channels")

        // No channels remain in enclave
        reportingSender.send("")
        assertEquals(2, reportingHandler.calls.size)
        assertEquals(0, channels.getChannelIds().size)
        assertEquals("0", reportingHandler.calls[1])
    }
}