package com.r3.conclave.common.internal

import com.r3.conclave.utilities.internal.getRemainingString
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import java.io.InputStream
import java.io.OutputStream
import java.lang.IllegalStateException
import java.net.ConnectException
import java.net.ServerSocket
import java.net.Socket
import java.nio.ByteBuffer
import java.util.concurrent.Semaphore

/**
 * Test the stream call interface classes.
 * It should be noted that the use of [EnclaveCallType] and [HostCallType] enum values in these tests are irrelevant.
 * There is no enclave or host present for any of these tests and the enums are re-used solely for testing purposes.
 */
class StreamCallInterfaceTest {
    companion object {
        private const val SOCKET_PORT_NUMBER = 31893
    }

    private lateinit var hostEnclaveInterface: CallInterface<EnclaveCallType, HostCallType>
    private lateinit var enclaveHostInterface: CallInterface<HostCallType, EnclaveCallType>

    private lateinit var hostSocket: Socket
    private lateinit var enclaveSocket: Socket

    @BeforeEach
    fun setup() {
        setupSockets()
        setupInterfaces()
    }

    @AfterEach
    fun teardown() {
        hostSocket.close()
        enclaveSocket.close()
    }

    /** Create a pair of sockets that are connected together. */
    private fun setupSockets() {
        val serverSocketReady = Semaphore(0)

        val serverSocketThread = Thread {
            ServerSocket(SOCKET_PORT_NUMBER).use { serverSocket ->
                serverSocketReady.release()
                hostSocket = serverSocket.accept()
            }
        }.apply { start() }

        serverSocketReady.acquire()

        for (retries in 0..10) {
            try {
                enclaveSocket = Socket("127.0.0.1", SOCKET_PORT_NUMBER)
                break
            } catch (e: ConnectException) {
                Thread.sleep(5)
            }
        }

        serverSocketThread.join()
    }

    /** Use reflection to instantiate the call interfaces, as they are not part of the common package. */
    private fun setupInterfaces() {
        val hostEnclaveInterfaceClass = Class.forName("com.r3.conclave.host.internal.StreamHostEnclaveInterface")
        val enclaveHostInterfaceClass = Class.forName("com.r3.conclave.enclave.internal.StreamEnclaveHostInterface")

        val hostEnclaveInterfaceConstructor = hostEnclaveInterfaceClass.getDeclaredConstructor(
                OutputStream::class.java, InputStream::class.java)
        val enclaveHostInterfaceConstructor = enclaveHostInterfaceClass.getDeclaredConstructor(
                OutputStream::class.java, InputStream::class.java, Int::class.java)

        hostEnclaveInterface = hostEnclaveInterfaceConstructor.newInstance(
                hostSocket.getOutputStream(), hostSocket.getInputStream()) as CallInterface<EnclaveCallType, HostCallType>
        enclaveHostInterface = enclaveHostInterfaceConstructor.newInstance(
                enclaveSocket.getOutputStream(), enclaveSocket.getInputStream(), 4) as CallInterface<HostCallType, EnclaveCallType>
    }

    @Test
    fun `host can call enclave`() {
        val inputString = "Test string!"
        val outputString: String?

        // Just echo the string back to the host side
        enclaveHostInterface.registerCallHandler(EnclaveCallType.CALL_MESSAGE_HANDLER, object : CallHandler {
            override fun handleCall(parameterBuffer: ByteBuffer): ByteBuffer { return parameterBuffer }
        })

        val inputBuffer = ByteBuffer.wrap(inputString.toByteArray())
        outputString = hostEnclaveInterface.executeOutgoingCall(EnclaveCallType.CALL_MESSAGE_HANDLER, inputBuffer)?.getRemainingString()

        assertThat(outputString).isEqualTo(inputString)
    }

    /**
     * In native mode, threads literally call the enclave from the host and return out of it. The enclave does not have
     * any threads of its own.
     * Because of this, the enclave cannot call the host without the host first calling the enclave. To simplify the
     * implementation of the stream call interfaces, they have been written such that this remains the case
     * (even though it need not necessarily be so).
     * This test checks to see if this behaviour is intact. This helps to ensure that there are as few
     * differences between the stream and native interfaces as possible.
     */
    @Test
    fun `enclave may not call host outside the context of an enclave call`() {
        val exception = assertThrows<IllegalStateException> {
            enclaveHostInterface.executeOutgoingCall(HostCallType.CALL_MESSAGE_HANDLER)
        }
        assertThat(exception).hasMessage("Outgoing host calls may not occur outside the context of an enclave call.")
    }

    @Test
    fun `enclave can call host inside the context of an enclave call`() {
        val inputString = "This is an input string!"
        var outputString: String? = null

        // Host handles call by echoing it
        hostEnclaveInterface.registerCallHandler(HostCallType.CALL_MESSAGE_HANDLER, object : CallHandler {
            override fun handleCall(parameterBuffer: ByteBuffer): ByteBuffer { return parameterBuffer }
        })

        // Enclave forwards call back to host
        enclaveHostInterface.registerCallHandler(EnclaveCallType.CALL_MESSAGE_HANDLER, object : CallHandler {
            override fun handleCall(parameterBuffer: ByteBuffer): ByteBuffer? {
                return enclaveHostInterface.executeOutgoingCall(HostCallType.CALL_MESSAGE_HANDLER, parameterBuffer)
            }
        })

        val inputBuffer = ByteBuffer.wrap(inputString.toByteArray())
        outputString = hostEnclaveInterface.executeOutgoingCall(EnclaveCallType.CALL_MESSAGE_HANDLER, inputBuffer)?.getRemainingString()

        assertThat(outputString).isEqualTo(inputString)
    }
}
