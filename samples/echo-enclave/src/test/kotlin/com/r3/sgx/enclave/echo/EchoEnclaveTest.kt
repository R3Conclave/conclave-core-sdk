package com.r3.sgx.enclave.echo

import com.r3.sgx.core.common.ChannelInitiatingHandler
import com.r3.sgx.core.host.EnclaveHandle
import com.r3.sgx.core.host.EnclaveLoadMode
import com.r3.sgx.core.host.NativeHostApi
import com.r3.sgx.testing.BytesRecordingHandler
import com.r3.sgx.testing.RootHandler
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.ByteBuffer
import kotlin.test.assertEquals

class EchoEnclaveTest {
    private val enclavePath = System.getProperty("com.r3.sgx.enclave.path")
                                  ?: throw AssertionError("System property 'com.r3.sgx.enclave.path' not set")
    private val handler = BytesRecordingHandler()

    private lateinit var enclaveHandle: EnclaveHandle<RootHandler.Connection>

    @Before
    fun setupEnclave() {
        enclaveHandle = NativeHostApi(EnclaveLoadMode.SIMULATION).createEnclave(RootHandler(), File(enclavePath))
    }

    @Test
    fun testEchoEnclave() {
        val connection = enclaveHandle.connection
        val channels = connection.addDownstream(ChannelInitiatingHandler())
        val (_, channel) = channels.addDownstream(handler).get()
        channel.send(ByteBuffer.wrap("Hello enclave".toByteArray()))
        assertEquals(1, handler.size)
        val buffer = handler.nextCall
        val response = ByteArray(buffer.remaining()).also { buffer.get(it) }
        assertEquals("Hello enclave", String(response))
    }

}
