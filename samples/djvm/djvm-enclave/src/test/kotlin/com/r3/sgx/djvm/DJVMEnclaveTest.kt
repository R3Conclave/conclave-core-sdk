package com.r3.sgx.djvm

import com.r3.sgx.core.common.ChannelInitiatingHandler
import com.r3.sgx.core.common.Sender
import com.r3.sgx.core.host.EnclaveHandle
import com.r3.sgx.djvm.enclave.DJVMEnclave
import com.r3.sgx.djvm.enclave.messages.MessageType
import com.r3.sgx.djvm.handlers.DJVMHandler
import com.r3.sgx.dynamictesting.EnclaveTestMode
import com.r3.sgx.dynamictesting.TestEnclavesBasedTest
import com.r3.sgx.testing.RootHandler
import org.assertj.core.api.Assertions.assertThat
import org.junit.AfterClass
import org.junit.Before
import org.junit.Test
import java.nio.file.Paths
import java.util.function.Consumer

class DJVMEnclaveTest : TestEnclavesBasedTest(EnclaveTestMode.Native) {

    companion object {
        val enclavePath = Paths.get(System.getProperty("enclave_path")
                ?: throw AssertionError("System property 'enclave_path' not set"))

        val userJarPath = Paths.get(System.getProperty("user-jar.path")
                ?: throw AssertionError("System property 'user-jar.path' not set."))

        private var isEnclaveInitialized = false
        private lateinit var enclaveHandle: EnclaveHandle<RootHandler.Connection>
        private lateinit var sender: Sender

        @JvmStatic
        @AfterClass
        fun destroy() {
            assertThat(isEnclaveInitialized).isTrue()
            sender.send(2 * Int.SIZE_BYTES, Consumer { buffer ->
                buffer.putInt(MessageType.JAR.ordinal)
                buffer.putInt(0)
            })
//            enclaveHandle.destroy()
        }

    }

    @Before
    fun setUp() {
        if (!isEnclaveInitialized) {
            enclaveHandle = createEnclaveWithHandler(RootHandler(), DJVMEnclave::class.java, enclavePath.toFile())

            val connection = enclaveHandle.connection
            val channels = connection.addDownstream(ChannelInitiatingHandler())
            val (_, sender) = channels.addDownstream(DJVMHandler()).get()
            DJVMEnclaveTest.sender = sender

            val userJar = userJarPath.toFile().readBytes()
            sender.send( 2 * Int.SIZE_BYTES + userJar.size, Consumer { buffer ->
                buffer.putInt(MessageType.JAR.ordinal)
                buffer.putInt(userJar.size)
                buffer.put(userJar)
            })

            isEnclaveInitialized = true
        }
    }

    @Test
    fun testKotlinTask() {
        val className = "com.r3.sgx.djvm.KotlinTask".toByteArray()
        val input = "Hello World".toByteArray()
        sendTask(className, input)
    }

    @Test
    fun testBadKotlinTask() {
        val className = "com.r3.sgx.djvm.BadKotlinTask".toByteArray()
        val input = "field".toByteArray()
        sendTask(className, input)
    }

    private fun sendTask(className: ByteArray, input: ByteArray) {
        sender.send(2 * Int.SIZE_BYTES + className.size + Int.SIZE_BYTES + input.size, Consumer { buffer ->
            buffer.putInt(MessageType.TASK.ordinal)
            buffer.putInt(className.size)
            buffer.put(className)
            buffer.putInt(input.size)
            buffer.put(input)
        })
    }
}