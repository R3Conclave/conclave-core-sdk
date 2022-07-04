package com.r3.conclave.integrationtests.tribuo.client

import com.r3.conclave.common.EnclaveInstanceInfo
import com.r3.conclave.integrationtests.tribuo.common.TribuoTask
import com.r3.conclave.integrationtests.tribuo.common.decode
import com.r3.conclave.mail.EnclaveMail
import com.r3.conclave.mail.PostOffice
import java.io.Closeable
import java.io.DataInputStream
import java.io.DataOutputStream
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket

class Client : Closeable {
    companion object {
        private val DEFAULT_ADDRESS = InetAddress.getLoopbackAddress()!!
        private const val DEFAULT_PORT = 9999
        private const val DEFAULT_TIMEOUT = 5000
    }

    val enclaveConfiguration = EnclaveConfiguration(this, System.getProperty("enclaveMode"))
    private var socket: Socket
    private var fromHost: DataInputStream
    private var toHost: DataOutputStream
    private var postOffice: PostOffice

    init {
        println("Attempting to connect to " + DEFAULT_ADDRESS.canonicalHostName + ':' + DEFAULT_PORT)
        socket = Socket()
        socket.connect(InetSocketAddress(DEFAULT_ADDRESS, DEFAULT_PORT), DEFAULT_TIMEOUT)
        fromHost = DataInputStream(socket.getInputStream())
        toHost = DataOutputStream(socket.getOutputStream())
        val attestation = receiveAttestation()
        println("Connected to $attestation")
        enclaveConfiguration.enclaveInstanceInfoChecker.check(attestation)
        postOffice = attestation.createPostOffice()
    }

    private fun receiveAttestation(): EnclaveInstanceInfo {
        val attestationBytes = ByteArray(fromHost.readInt())
        fromHost.readFully(attestationBytes)
        return EnclaveInstanceInfo.deserialize(attestationBytes)
    }

    fun sendMail(body: ByteArray) {
        val encryptedMail = postOffice.encryptMail(body)
        toHost.writeInt(encryptedMail.size)
        toHost.write(encryptedMail)
        toHost.flush()
    }

    fun receiveMail(): EnclaveMail {
        val encryptedReply = ByteArray(fromHost.readInt())
        fromHost.readFully(encryptedReply)
        return postOffice.decryptMail(encryptedReply)
    }

    /**
     * Serialize and send a request to the enclave and deserialize the response.
     * @param task the request to the enclave.
     * @return the deserialized response from the enclave.
     */
    inline fun <reified R> sendAndReceive(task: TribuoTask): R {
        sendMail(task.encode())
        return decode(receiveMail().bodyAsBytes)
    }

    /**
     * Send a request to the enclave to write a file.
     * @param path file path.
     * @param contentModifier function to modify the file contents before sending it to the enclave.
     * Only used in mock mode.
     * @return file path in the enclave.
     */
    fun sendResource(path: String, contentModifier: ((ByteArray) -> (ByteArray))? = null): String {
        return enclaveConfiguration.fileManager.sendFile(path, contentModifier)
    }

    /**
     * Send a request to the enclave to delete a file.
     * @param path file path.
     * @param contentModifier function to modify the file content instead of deleting it.
     * Only used in mock mode.
     */
    fun deleteFile(path: String, contentModifier: ((ByteArray) -> (ByteArray))? = null) {
        enclaveConfiguration.fileManager.deleteFile(path, contentModifier)
    }

    override fun close() {
        fromHost.close()
        toHost.close()
        socket.close()
    }

    fun resolve(path: String): String {
        return enclaveConfiguration.fileManager.resolve(path)
    }
}
