package com.r3.conclave.mail.internal

import com.r3.conclave.mail.MinSizePolicy
import com.r3.conclave.mail.internal.noise.protocol.Noise
import java.io.ByteArrayOutputStream
import java.security.PrivateKey
import java.security.PublicKey

abstract class AbstractPostOffice {
    abstract val destinationPublicKey: PublicKey
    abstract val topic: String

    protected abstract val senderPrivateKey: PrivateKey
    protected abstract val keyDerivation: ByteArray?

    protected var sequenceNumber: Long = 0
    protected var encryptCalled = false

    private var _minSizePolicy: MinSizePolicy? = null

    // Moving average seems like a sensible compromise against largest seen's behaviour of bloating small mail if just
    // one very large mail is created.
    open var minSizePolicy: MinSizePolicy
        get() = _minSizePolicy ?: MinSizePolicy.movingAverage().also { _minSizePolicy = it }
        set(value) {
            _minSizePolicy = value
        }

    @Synchronized
    open fun encryptMail(body: ByteArray, envelope: ByteArray?): ByteArray {
        encryptCalled = true
        val header = EnclaveMailHeaderImpl(sequenceNumber++, topic, envelope, keyDerivation)
        val minSize = minSizePolicy.getMinSize(body.size)
        val output = ByteArrayOutputStream(getExpectedSize(header, minSize, body))
        val stream = MailEncryptingStream(output, destinationPublicKey, header, senderPrivateKey, minSize)
        stream.write(body)
        stream.close()
        return output.toByteArray()
    }

    private fun getExpectedSize(header: EnclaveMailHeaderImpl, minSize: Int, body: ByteArray): Int {
        // See MailEncryptingStream.maybeHandshake for the header format.
        // The 1 is for the single byte protocol ID.
        val prologueSize = 1 + header.encodedSize()
        val payloadSize = maxOf(body.size, minSize)
        val packetCount = (payloadSize / MailEncryptingStream.MAX_PACKET_PAYLOAD_LENGTH) + 1
        // The 2 is for the prologue size field.
        return 2 + prologueSize + MailProtocol.SENDER_KEY_TRANSMITTED.handshakeLength + (packetCount * PACKET_OVERHEAD) + payloadSize
    }

    companion object {
        private const val PACKET_OVERHEAD = Noise.MAX_PACKET_LEN - MailEncryptingStream.MAX_PACKET_PAYLOAD_LENGTH

        fun checkTopic(topic: String) {
            require(topic.isNotBlank()) { "Topic must not be blank" }
            require(topic.length < 256) { "Topic length must be less than 256 characters, is ${topic.length}" }
            for ((index, char) in topic.withIndex()) {
                require(char.isLetterOrDigit() || char == '-') { "Character $index of the topic is not a character, digit or -" }
            }
        }
    }
}
