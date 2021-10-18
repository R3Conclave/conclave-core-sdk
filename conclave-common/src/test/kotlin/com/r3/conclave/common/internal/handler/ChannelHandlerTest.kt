package com.r3.conclave.common.internal.handler

import com.r3.conclave.enclave.Enclave
import com.r3.conclave.enclave.internal.EnclaveEnvironment
import com.r3.conclave.enclave.internal.ExceptionSendingHandler
import com.r3.conclave.enclave.internal.InternalEnclave
import com.r3.conclave.host.internal.MockEnclaveHandle
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import java.util.stream.LongStream
import kotlin.collections.set

class ChannelHandlerTest {
    class AddingHandler : StringHandler() {
        private val sum = AtomicLong(0)
        override fun onReceive(sender: StringSender, string: String) {
            when (string) {
                "GET_SUM" -> sender.send(sum.get().toString())
                else -> sum.addAndGet(string.toLong())
            }
        }
    }

    class ChannelsSizeReportingHandler(private val channels: ChannelHandlingHandler.Connection) : StringHandler() {
        override fun onReceive(sender: StringSender, string: String) {
            sender.send(channels.getChannelIds().size.toString())
        }
    }

    class ChannelAddingEnclave : InternalEnclave, Enclave() {
        override fun internalInitialise(env: EnclaveEnvironment, upstream: Sender): HandlerConnected<*> {
            val connected = HandlerConnected.connect(ExceptionSendingHandler(exposeErrors = true), upstream)
            val mux = connected.connection.setDownstream(SimpleMuxingHandler())
            val channels = mux.addDownstream(object : ChannelHandlingHandler() {
                override fun createHandler() = AddingHandler()
            })
            mux.addDownstream(ChannelsSizeReportingHandler(channels))
            return connected
        }
    }

    @Test
    fun `open send close`() {
        val addingHandler = StringRecordingHandler()
        val reportingHandler = StringRecordingHandler()
        val root = createEnclave<ChannelAddingEnclave>()
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
    fun `parallel channels`() {
        val addingHandler = StringRecordingHandler()
        val reportingHandler = StringRecordingHandler()
        val root = createEnclave<ChannelAddingEnclave>()
        val channels = root.addDownstream(ChannelInitiatingHandler())
        val reportingSender = root.addDownstream(reportingHandler)

        val muxIds = Collections.synchronizedList(ArrayList<MuxId>())
        val senders = ConcurrentHashMap<MuxId, StringSender>()
        val n = 10000L
        val random = Random()
        println("Sending $n numbers for summing")
        LongStream.rangeClosed(1, n).parallel().forEach { number ->
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
        println("${senders.size} channels created")

        println("Getting each channel's sum")
        // Get each channel's sum
        for (muxId in 0 until muxIds.size) {
            senders[muxId]!!.send("GET_SUM")
        }

        // Every channel reported
        assertEquals(senders.size, addingHandler.calls.size)
        val overallSum = addingHandler.calls.stream().mapToLong(java.lang.Long::parseLong).sum()
        assertEquals(n * (n + 1) / 2, overallSum)
        println("Overall sum $overallSum")

        println("Closing all channels")
        // Close all channels in parallel
        senders.keys.parallelStream().forEach { muxId ->
            channels.removeDownstream(muxId)
        }
        println("Closed ${senders.keys.size} channels")

        // No channels remain in enclave
        reportingSender.send("")
        assertEquals(2, reportingHandler.calls.size)
        assertEquals(0, channels.getChannelIds().size)
        assertEquals("0", reportingHandler.calls[1])
    }

    private inline fun <reified E : Enclave> createEnclave(): RootHandler.Connection {
        return MockEnclaveHandle(E::class.java.getConstructor().newInstance(), null, RootHandler()).connection
    }
}
