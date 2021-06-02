package com.r3.conclave.cordapp.sample.enclave

import com.r3.conclave.cordapp.common.AnonymousSender
import com.r3.conclave.cordapp.common.SenderIdentity
import com.r3.conclave.cordapp.common.internal.SenderIdentityImpl
import com.r3.conclave.enclave.Enclave
import com.r3.conclave.enclave.EnclavePostOffice
import com.r3.conclave.mail.EnclaveMail
import java.io.ByteArrayInputStream
import java.io.DataInputStream
import java.lang.Exception
import java.security.PublicKey
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.util.logging.Logger

/**
 * The CordaEnclave class extends the [Enclave] class with CorDapp's application level concerns.
 */
abstract class CordaEnclave : Enclave() {

    /**
     * Tries to authenticate the sender by verifying that the provided certificate chain is trusted, and that
     * the sender is actually the subject of the certificate by verifying the signed secret against the subject's
     * public key.
     */
    private fun tryAuthenticateAndStoreIdentity(
        mail: EnclaveMail
    ): Boolean {
        val bais = ByteArrayInputStream(mail.bodyAsBytes)
        val dis = DataInputStream(bais)
        val isAnonymousSender = dis.readBoolean()
        if (isAnonymousSender) {
            val authenticated = true
            return authenticated
        }

        val identity = SenderIdentityImpl.deserialize(dis)
        val sharedSecret = mail.authenticatedSender.encoded
        val authenticated = authenticateIdentity(sharedSecret, identity)

        if (authenticated) {
            storeIdentity(mail.authenticatedSender, identity)
        }
        return authenticated
    }

    private fun authenticateIdentity(sharedSecret: ByteArray, identity: SenderIdentityImpl): Boolean {
        val isTrusted = identity.isTrusted(trustedRootCertificate)
        val didSign: Boolean = identity.didSign(sharedSecret)
        val authenticated = isTrusted && didSign
        return authenticated
    }

    private fun storeIdentity(authenticatedSender: PublicKey, identity: SenderIdentityImpl) {
        synchronized(identities) {
            identities.put(authenticatedSender, identity)
        }
    }

    /**
     * Retrieve a cached identity based on encrypted public key of the sender.
     * @return the sender's identity if the sender sent it, an anonymous identity otherwise.
     */
    private fun getSenderIdentity(authenticatedSenderKey: PublicKey): SenderIdentity {
        synchronized(identities) {
            return identities.computeIfAbsent(authenticatedSenderKey) { key: PublicKey? ->
                AnonymousSender(key!!)
            }
        }
    }

    /**
     * This class extends the EnclaveMail by adding Corda/CorDapp application concerns. Currently this is limited to
     * the sender identity.
     */
    class CordaEnclaveMail private constructor(private val mail: EnclaveMail, val senderIdentity: SenderIdentity) :
        EnclaveMail {
        override fun getSequenceNumber(): Long = mail.sequenceNumber
        override fun getTopic(): String = mail.topic
        override fun getEnvelope(): ByteArray? = mail.envelope
        override fun getAuthenticatedSender(): PublicKey = mail.authenticatedSender
        override fun getBodyAsBytes(): ByteArray = mail.bodyAsBytes

        companion object {
            internal fun create(mail: EnclaveMail, senderIdentity: SenderIdentity) =
                CordaEnclaveMail(mail, senderIdentity)
        }
    }

    /**
     * The default Enclave's message callback that is invoked when a mail has been delivered by the host
     * is overridden here to handle Corda/CorDapp application level concerns. Messages that are not meant to be
     * processed at this level, are forwarded to the [CordaEnclave.receiveCordaMail] callback.
     */
    final override fun receiveMail(id: Long, mail: EnclaveMail, routingHint: String?) {
        if (isTopicFirstMessage(mail)) {
            // only login supported so far
            val authenticated = tryAuthenticateAndStoreIdentity(mail)
            val result = if (authenticated) "-ack" else "-nak"
            val postOffice: EnclavePostOffice = postOffice(mail.authenticatedSender, mail.topic + result)
            val reply: ByteArray = postOffice.encryptMail(emptyBytes)
            postMail(reply, routingHint)
        } else {
            val identity = getSenderIdentity(mail.authenticatedSender)
            val cordaMail = CordaEnclaveMail.create(mail, identity)
            receiveCordaMail(id, cordaMail, routingHint)
        }
    }

    /**
     * Invoked when a [CordaEnclave.receiveMail] forwards the message ahead for further processing.
     *
     * Any uncaught exceptions thrown by this method propagate to the calling `EnclaveHost.deliverMail`. In Java, checked
     * exceptions can be made to propagate by rethrowing them in an unchecked one.
     *
     * @param id An opaque identifier for the mail.
     * @param mail Access to the decrypted/authenticated mail body+envelope.
     * @param routingHint An optional string provided by the host that can be passed to [postMail] to tell the
     * host that you wish to reply to whoever provided it with this mail (e.g. connection ID). Note that this may
     * not be the same as the logical sender of the mail if advanced anonymity techniques are being used, like
     * users passing mail around between themselves before it's delivered.
     */
    protected abstract fun receiveCordaMail(id: Long, mail: CordaEnclaveMail, routingHint: String?)

    companion object {
        private const val topicFirstMessageSequenceNumber = 0L
        private val emptyBytes = ByteArray(0)
        private val identities: HashMap<PublicKey, SenderIdentity> = HashMap()
        private const val trustedRootCertificateResourcePath = "/trustedroot.cer"
        private val trustedRootCertificate: X509Certificate = getTrustedRootCertificate(trustedRootCertificateResourcePath)

        private fun getTrustedRootCertificate(trustedRootCertificateResourcePath: String): X509Certificate {
            try {
                CordaEnclave::class.java.getResourceAsStream(trustedRootCertificateResourcePath).use {
                    val certificateFactory: CertificateFactory = CertificateFactory.getInstance("X509")
                    return certificateFactory.generateCertificate(it) as X509Certificate
                }
            } catch (exception: Exception) {
                // Log an error message to let people know what went wrong and throw the exception again to ensure
                // the behaviour related to exceptions remains the same
                Logger.getGlobal().severe("Failed to load trusted root certificate. Please ensure the resource exists and it is not corrupted. Resource path: $trustedRootCertificateResourcePath")
                throw exception
            }
        }

        private fun isTopicFirstMessage(mail: EnclaveMail): Boolean {
            return mail.sequenceNumber == topicFirstMessageSequenceNumber
        }
    }
}
