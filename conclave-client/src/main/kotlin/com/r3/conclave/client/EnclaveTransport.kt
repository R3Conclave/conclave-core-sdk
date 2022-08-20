package com.r3.conclave.client

import com.r3.conclave.common.EnclaveException
import com.r3.conclave.common.EnclaveInstanceInfo
import com.r3.conclave.mail.MailDecryptionException
import java.io.IOException

/**
 * Represents a transport layer between the client and enclave host. [EnclaveTransport] defines how a host needs
 * to behave to be able to support [EnclaveClient] objects that connect to it. It is responsible for transporting
 * encrypted bytes between the host and client, whilst [EnclaveClient] deals with the encryption and decryption and
 * retry attempts if the enclave is restarted.
 *
 * Details such as how to start and shutdown the transport are implementation specific.
 *
 * [com.r3.conclave.client.web.WebEnclaveTransport] is a HTTP RESTful implementation for connecting the client to a
 * host which is using the `conclave-web-host` module.
 *
 * @see EnclaveClient
 * @see com.r3.conclave.client.web.WebEnclaveTransport
 */
interface EnclaveTransport {
    /**
     * Retrieve the latest version of the enclave's [EnclaveInstanceInfo] from the host.
     *
     * The implementation will most likely need to call [EnclaveInstanceInfo.deserialize] once the bytes have been
     * received from the host.
     *
     * @throws IOException If there's an I/O error.
     * @throws IllegalArgumentException If there's an issue deserializing the [EnclaveInstanceInfo].
     */
    @Throws(IOException::class)
    fun enclaveInstanceInfo(): EnclaveInstanceInfo

    /**
     * A request to connect the given client to the host. This is called when the client calls [EnclaveClient.start].
     *
     * @return An [ClientConnection] representing the connection between the client and the host.
     * @throws IOException If there's an issue connecting to the host.
     *
     * @see EnclaveClient.start
     */
    @Throws(IOException::class)
    fun connect(client: EnclaveClient): ClientConnection


    /**
     * Represents the connection between an [EnclaveClient] and an enclave host. A connection is created when the client
     * calls [EnclaveClient.start] which then goes on to call [EnclaveTransport.connect]. There may be multiple clients
     * connected to a single [EnclaveTransport] and each connection must be able to disambiguate between all of them.
     */
    interface ClientConnection {
        /**
         * Send the encrypted mail bytes to the host for delivery to the enclave. This method must block until the
         * enclave processes the mail. If the enclave produces a synchronous response mail then that is returned back
         * here.
         *
         * If the enclave is unable to decrypt the mail bytes then the host must indicate so and this method must throw
         * a [MailDecryptionException]. The client will catch this and attempt redelivery of the original message by
         * using a fresh new copy of the [EnclaveInstanceInfo] from the host.
         *
         * If the enclave itself threw an exception then this method must throw an [EnclaveException]. The message or
         * may not contain the original message.
         *
         * @param encryptedMailBytes The mail bytes to send to the host for delivery to the enclave.
         * @return The synchronous mail response from the enclave, or null if there wasn't one.
         * @throws MailDecryptionException If the enclave was unable to decrypt the mail bytes.
         * @throws EnclaveException If the enclave threw an exception.
         * contain the original message thrown by the enclave. In particular,
         * @throws IOException If there's an I/O error or some other issue with the sending or receiving.
         */
        @Throws(IOException::class, MailDecryptionException::class)
        fun sendMail(encryptedMailBytes: ByteArray): ByteArray?

        /**
         * Send a poll request to the host for retreiving the next available asychronous encrypted mail response from
         * the enclave. If there is one then it is returned here. Otherwise this must return null.
         *
         * @return The next asychronous mail response as the encrypted bytes or `null` if there isn't one.
         * @throws IOException If there's an issue polling or receiving the asychronous mail.
         */
        @Throws(IOException::class)
        fun pollMail(): ByteArray?

        /**
         * Disconnect the client from the [EnclaveTransport].
         *
         * @throws IOException If there's an I/O error.
         */
        @Throws(IOException::class)
        fun disconnect()
    }
}
