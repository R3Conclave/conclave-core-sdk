package com.r3.conclave.cordapp.sample.client

import co.paralleluniverse.fibers.Suspendable
import com.r3.conclave.common.EnclaveInstanceInfo
import com.r3.conclave.cordapp.common.internal.SenderIdentityImpl
import com.r3.conclave.mail.Curve25519PrivateKey
import com.r3.conclave.mail.EnclaveMail
import com.r3.conclave.mail.PostOffice
import net.corda.core.flows.FlowException
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.FlowSession
import net.corda.core.utilities.unwrap
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.io.IOException
import java.security.SecureRandom

/**
 * A helper class that wraps some boilerplate for flow initiators to communicate with enclaves.
 */
class EnclaveFlowInitiator(
    private val flow: FlowLogic<*>,
    private val session: FlowSession,
    private val attestation: EnclaveInstanceInfo
) {
    private val encryptionKey = Curve25519PrivateKey.random()
    private val flowTopic: String = flow.runId.uuid.toString()
    private val postOffice: PostOffice = attestation.createPostOffice(encryptionKey, flowTopic)

    /**
     * Builds a mailer identity based on the session encryption key pair, the node identity and certificates.
     */
    @Suspendable
    private fun buildMailerIdentity(): SenderIdentityImpl {
        val sharedSecret = encryptionKey.publicKey.encoded
        val signerPublicKey = flow.ourIdentity.owningKey
        val signature = flow.serviceHub.keyManagementService.sign(sharedSecret, signerPublicKey).withoutKey()
        val signerCertPath = flow.ourIdentityAndCert.certPath
        return SenderIdentityImpl(signerCertPath, signature.bytes)
    }

    /**
     * Sends a message to the Enclave
     * @param messageBytes the serialized message body bytes
     * @throws FlowException
     */
    @Suspendable
    @Throws(FlowException::class)
    private fun sendToEnclave(messageBytes: ByteArray) {
        val encryptedMail = postOffice.encryptMail(messageBytes)
        session.send(encryptedMail)
    }

    /**
     * Sends a message to the Enclave and waits for the response
     * @param messageBytes the serialized message body bytes
     * @return the serialized message body bytes of the Enclave response
     * @throws FlowException
     */
    @Suspendable
    @Throws(FlowException::class)
    fun sendAndReceive(messageBytes: ByteArray): ByteArray {
        sendToEnclave(messageBytes)
        return receiveFromEnclave()
    }

    /**
     * Sends a message to the Enclave with the Party identity and wait for the acknowledge
     * @throws FlowException
     */
    @Suspendable
    @Throws(FlowException::class)
    fun sendIdentityToEnclave(isAnonymous: Boolean) {

        val serializedIdentity = getSerializedIdentity(isAnonymous)
        sendToEnclave(serializedIdentity)

        val mail: EnclaveMail = session.receive(ByteArray::class.java).unwrap { mail: ByteArray ->
            try {
                postOffice.decryptMail(mail)
            } catch (e: IOException) {
                throw FlowException("Unable to decrypt mail from Enclave", e)
            }
        }

        if (!mail.topic.contentEquals("$flowTopic-ack"))
            throw FlowException("The enclave could not validate the identity sent")
    }

    @Suspendable
    private fun getSerializedIdentity(isAnonymous: Boolean): ByteArray {
        val baos = ByteArrayOutputStream()
        val dos = DataOutputStream(baos)

        dos.writeBoolean(isAnonymous)
        if (isAnonymous) {
            // It is important to ensure the identity message has roughly the same size whether the user is anonymous or
            // not. This prevents people from gaining insights while performing statistical analyses to the message sizes
            // sent over the network. Failing to do so might leak information whether a user is anonymous or not.
            // N.B. Although enclave PostOffice offers a feature which automatically pads all messages with a minimum
            // fix size, we should not use such feature for this scenario to avoid wasting bandwidth.
            // The identity message is only sent when a flow starts and occupies a few KBs. Padding all messages
            // to have a few KBs in size would be a waste of resources.
            addPadding(dos, identityAverageSize + generateRandomNumber(0, 128))
        } else {
            val identity = buildMailerIdentity()
            identity.serialize(dos)
        }

        return baos.toByteArray()
    }

    private fun generateRandomNumber(min: Int, max: Int): Int {
        return (SecureRandom.getInstanceStrong().nextInt(max - min + 1) + min)
    }

    private fun addPadding(dos: DataOutputStream, size: Int) {
        val byteArray = ByteArray(size)
        dos.write(byteArray)
    }

    /**
     * Receives and decrypt a message from the Enclave
     * @return the serialized message body bytes from the Enclave
     * @throws FlowException
     */
    @Suspendable
    @Throws(FlowException::class)
    fun receiveFromEnclave(): ByteArray {
        val reply: EnclaveMail = session.receive(ByteArray::class.java).unwrap { mail: ByteArray ->
            try {
                postOffice.decryptMail(mail)
            } catch (e: IOException) {
                throw FlowException("Unable to decrypt mail from Enclave", e)
            }
        }
        return reply.bodyAsBytes
    }

    companion object {
        // The value used here was determined by looking at the size of the byte array generated when the identity
        // instance is serialized for a non-anonymous user in sendIdentityToEnclave(...).
        // Maybe this value should be configurable via an environment variable in case the average size for a login message
        // changes for other use cases.
        private const val identityAverageSize = 2300
    }
}
