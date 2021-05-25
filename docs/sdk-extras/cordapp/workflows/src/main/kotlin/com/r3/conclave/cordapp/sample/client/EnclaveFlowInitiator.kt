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
import java.io.IOException
import java.util.*

/**
 * A helper class that wraps some boilerplate for flow initiators to communicate with enclaves.
 */
class EnclaveFlowInitiator(
    private val flow: FlowLogic<*>,
    private val session: FlowSession,
    private val attestation: EnclaveInstanceInfo
) {
    private val encryptionKey = Curve25519PrivateKey.random()
    private val postOffices = HashMap<String, PostOffice>()
    private val flowTopic: String = flow.runId.uuid.toString()

    init {
        postOffices[flowTopic] = attestation.createPostOffice(encryptionKey, flowTopic)
    }

    /**
     * Holds one [PostOffice] instance per topic
     * @param topic the topic for which a PostOffice instance must be created and/or retrieved
     * @return the PostOffice instance for the given topic
     */
    @Suspendable
    private fun getOrCreatePostOffice(topic: String): PostOffice {
        return postOffices.computeIfAbsent(topic) { t: String ->
            attestation.createPostOffice(encryptionKey, t)
        }
    }

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
     * @param topic the message topic
     * @param messageBytes the serialized message body bytes
     * @throws FlowException
     */
    @Suspendable
    @Throws(FlowException::class)
    private fun sendToEnclave(topic: String, messageBytes: ByteArray) {
        val postOffice = getOrCreatePostOffice(topic)
        val encryptedMail = postOffice.encryptMail(messageBytes)
        session.send(encryptedMail)
    }

    /**
     * Sends a message to the Enclave and waits for the response
     * @param topic the message topic
     * @param messageBytes the serialized message body bytes
     * @return the serialized message body bytes of the Enclave response
     * @throws FlowException
     */
    @Suspendable
    @Throws(FlowException::class)
    private fun sendAndReceive(topic: String, messageBytes: ByteArray): ByteArray {
        sendToEnclave(topic, messageBytes)
        return receiveFromEnclave()
    }

    /**
     * Sends a message to the Enclave with the Party identity and wait for the acknowledge
     * @throws FlowException
     */
    @Suspendable
    @Throws(FlowException::class)
    fun sendIdentityToEnclave() {
        val identity = buildMailerIdentity()
        val topic = "--conclave-login"
        sendToEnclave(topic, identity.serialize())
        val mail: EnclaveMail = session.receive(ByteArray::class.java).unwrap { mail: ByteArray? ->
            try {
                postOffices[topic]!!.decryptMail(mail!!)
            } catch (e: IOException) {
                throw FlowException("Unable to decrypt mail from Enclave", e)
            }
        }

        if(!mail.topic.contentEquals("$topic-ack"))
            throw FlowException("The enclave could not validate the identity sent")
    }

    /**
     * Sends a message to the Enclave and reads the response
     * @param messageBytes the serialized message body bytes
     * @return the serialized message body bytes of the Enclave response
     * @throws FlowException
     */
    @Suspendable
    @Throws(FlowException::class)
    fun sendAndReceive(messageBytes: ByteArray): ByteArray {
        return sendAndReceive(flowTopic, messageBytes)
    }

    /**
     * Receives and decrypt a message from the Enclave
     * @return the serialized message body bytes from the Enclave
     * @throws FlowException
     */
    @Suspendable
    @Throws(FlowException::class)
    fun receiveFromEnclave(): ByteArray {
        val postOffice = postOffices[flowTopic]
        val reply: EnclaveMail = session.receive(ByteArray::class.java).unwrap { mail: ByteArray? ->
            try {
                postOffice!!.decryptMail(mail!!)
            } catch (e: IOException) {
                throw FlowException("Unable to decrypt mail from Enclave", e)
            }
        }
        return reply.bodyAsBytes
    }
}
