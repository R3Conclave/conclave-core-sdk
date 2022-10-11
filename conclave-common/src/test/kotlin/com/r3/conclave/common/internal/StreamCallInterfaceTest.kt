package com.r3.conclave.common.internal

import com.r3.conclave.utilities.internal.getRemainingString
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.io.InputStream
import java.io.OutputStream
import java.net.ConnectException
import java.net.ServerSocket
import java.net.Socket
import java.nio.ByteBuffer

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
        val serverSocketThread = Thread {
            ServerSocket(SOCKET_PORT_NUMBER).use { serverSocket ->
                hostSocket = serverSocket.accept()
            }
        }
        serverSocketThread.start()

        var connectionAttempts = 0
        var maybeEnclaveSocket: Socket? = null
        while (connectionAttempts < 20) {
            try {
                maybeEnclaveSocket = Socket("127.0.0.1", SOCKET_PORT_NUMBER)
            } catch (e: ConnectException) {
                connectionAttempts++
                Thread.sleep(1)
            }
        }

        enclaveSocket = checkNotNull(maybeEnclaveSocket) { "Failed to open enclave socket." }
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
        var outputString: String? = null
        enclaveHostInterface.registerCallHandler(EnclaveCallType.START_ENCLAVE, object : CallHandler {
            override fun handleCall(parameterBuffer: ByteBuffer): ByteBuffer? {
                outputString = parameterBuffer.getRemainingString()
                return null
            }
        })
        val inputBuffer = ByteBuffer.wrap(inputString.toByteArray())
        hostEnclaveInterface.executeOutgoingCall(EnclaveCallType.START_ENCLAVE, inputBuffer)
        assertThat(outputString).isEqualTo(outputString)
    }
}
