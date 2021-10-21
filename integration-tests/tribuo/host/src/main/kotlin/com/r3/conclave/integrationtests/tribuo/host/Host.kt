package com.r3.conclave.integrationtests.tribuo.host

import com.r3.conclave.common.EnclaveInstanceInfo
import com.r3.conclave.common.OpaqueBytes
import com.r3.conclave.host.AttestationParameters
import com.r3.conclave.host.EnclaveHost
import com.r3.conclave.host.MailCommand
import com.r3.conclave.host.PlatformSupportException
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException
import java.net.ServerSocket
import java.nio.file.Paths

class Host {
    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            if (EnclaveHost.isHardwareEnclaveSupported()) {
                println("This platform supports enclaves in simulation, debug and release mode.")
            } else if (EnclaveHost.isSimulatedEnclaveSupported()) {
                println("This platform does not support hardware enclaves, but does support enclaves in simulation.")
                println("Attempting to enable hardware enclave support...")
                try {
                    EnclaveHost.enableHardwareEnclaveSupport()
                    println("Hardware support enabled!")
                } catch (e: PlatformSupportException) {
                    println("Failed to enable hardware enclave support. Reason: ${e.message}")
                }
            } else {
                println("This platform supports enclaves in mock mode only.")
            }
            val port = 9999
            println("Listening on port $port.")
            ServerSocket(port).use { acceptor ->
                acceptor.accept().use { connection ->
                    DataOutputStream(connection.getOutputStream()).use { output ->
                        EnclaveHost.load("com.r3.conclave.integrationtests.tribuo.enclave.TribuoEnclave", Paths.get("./tribuo_test.disk")).use { enclave ->
                            enclave.start(initializeAttestationParameters(), null) { commands: List<MailCommand?> ->
                                for (command in commands) {
                                    if (command is MailCommand.PostMail) {
                                        try {
                                            sendArray(output, command.encryptedBytes)
                                        } catch (e: IOException) {
                                            System.err.println("Failed to send reply to client.")
                                            e.printStackTrace()
                                        }
                                    }
                                }
                            }
                            val attestation = enclave.enclaveInstanceInfo
                            val attestationBytes = attestation.serialize()
                            println(EnclaveInstanceInfo.deserialize(attestationBytes))
                            sendArray(output, attestationBytes)

                            DataInputStream(connection.getInputStream()).use { input ->
                                // Forward mails to the enclave
                                try {
                                    while (true) {
                                        val mailBytes = ByteArray(input.readInt())
                                        input.readFully(mailBytes)

                                        // Deliver it to the enclave
                                        enclave.deliverMail(mailBytes, null)
                                    }
                                } catch (_: IOException) {
                                    println("Client closed the connection.")
                                }
                            }
                        }
                    }
                }
            }
        }

        @Throws(IOException::class)
        private fun sendArray(stream: DataOutputStream, bytes: ByteArray) {
            stream.writeInt(bytes.size)
            stream.write(bytes)
            stream.flush()
        }

        private fun initializeAttestationParameters(): AttestationParameters {
            if ("epid" == System.getProperty("attestationMode")) {
                val spid = OpaqueBytes.parse(System.getProperty("conclave.spid"))
                val attestationKey = System.getProperty("conclave.attestation-key")
                return AttestationParameters.EPID(spid, attestationKey)
            }
            return AttestationParameters.DCAP()
        }
    }
}
