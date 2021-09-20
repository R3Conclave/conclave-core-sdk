package com.r3.conclave.mail.internal

import com.r3.conclave.mail.EnclaveMail
import java.security.PublicKey

class DecryptedEnclaveMail(
    override val sequenceNumber: Long,
    override val topic: String,
    override val authenticatedSender: PublicKey,
    private val _envelope: ByteArray?,
    val privateHeader: ByteArray?,
    private val _bodyAsBytes: ByteArray,
) : EnclaveMail {
    override val envelope: ByteArray? get() = _envelope?.clone()
    override val bodyAsBytes: ByteArray get() = _bodyAsBytes.clone()
}