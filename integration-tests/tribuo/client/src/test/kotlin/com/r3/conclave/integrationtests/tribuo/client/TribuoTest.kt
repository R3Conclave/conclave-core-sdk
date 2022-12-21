package com.r3.conclave.integrationtests.tribuo.client

import com.r3.conclave.common.EnclaveInstanceInfo
import com.r3.conclave.host.EnclaveHost
import com.r3.conclave.host.MailCommand
import com.r3.conclave.integrationtests.general.commontest.TestUtils
import com.r3.conclave.integrationtests.general.commontest.TestUtils.simulationOnlyTest
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.io.TempDir
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException
import java.net.ServerSocket
import java.net.Socket
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread
import kotlin.io.path.div

open class TribuoTest {
    companion object {
        private lateinit var enclaveHost: EnclaveHost
        private lateinit var serverSocket: ServerSocket
        private lateinit var clientSocket: Socket

        @TempDir
        @JvmField
        var tempDirectory: Path? = null
        lateinit var client: Client

        @BeforeAll
        @JvmStatic
        fun start() {
            // CON-1280: Multiple Tribuo tests fail for Gramine in debug mode
            if (TestUtils.runtimeType == TestUtils.RuntimeType.GRAMINE) {
                simulationOnlyTest()
            }
            enclaveHost = EnclaveHost.load("com.r3.conclave.integrationtests.tribuo.enclave.TribuoEnclave")
            val port = 9999
            println("Listening on port $port.")
            serverSocket = ServerSocket(port)

            val hostFailed = AtomicBoolean(false)
            thread {
                startHost()
            }.also {
                it.uncaughtExceptionHandler = Thread.UncaughtExceptionHandler { _, e ->
                    e.printStackTrace()
                    hostFailed.set(true)
                }
            }
            while (!hostFailed.get()) {
                try {
                    client = Client()
                    break
                } catch (e: Exception) {
                    println("Retrying: " + e.message)
                    Thread.sleep(2000)
                }
            }
            check(!hostFailed.get()) { "Host failed to start." }
        }

        @AfterAll
        @JvmStatic
        fun close() {
            if (::serverSocket.isInitialized) {
                serverSocket.close()
            }
            if (::enclaveHost.isInitialized) {
                enclaveHost.close()
            }
        }

        // TODO Use exising enclave test action framework
        private fun startHost() {
            clientSocket = serverSocket.accept()

            val clientOutput = DataOutputStream(clientSocket.getOutputStream())
            enclaveHost.start(
                TestUtils.getAttestationParams(enclaveHost),
                null,
                tempDirectory!! / "enclave-fs.data"
            ) { commands: List<MailCommand> ->
                for (command in commands) {
                    if (command is MailCommand.PostMail) {
                        sendArray(clientOutput, command.encryptedBytes)
                    }
                }
            }
            val attestationBytes = enclaveHost.enclaveInstanceInfo.serialize()
            println(EnclaveInstanceInfo.deserialize(attestationBytes))
            sendArray(clientOutput, attestationBytes)

            val clientInput = DataInputStream(clientSocket.getInputStream())
            // Forward mails to the enclave
            try {
                while (true) {
                    val mailBytes = ByteArray(clientInput.readInt())
                    clientInput.readFully(mailBytes)
                    // Deliver it to the enclave
                    enclaveHost.deliverMail(mailBytes, null)
                }
            } catch (_: IOException) {
                println("Client closed the connection.")
            }
        }

        private fun sendArray(stream: DataOutputStream, bytes: ByteArray) {
            stream.writeInt(bytes.size)
            stream.write(bytes)
            stream.flush()
        }
    }
}
