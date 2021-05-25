package com.r3.conclave.cordapp.common

import java.security.PublicKey

/**
 * Represents a sender that did not send its identity
 */
class AnonymousSender(private val authenticatedSenderKey: PublicKey) : SenderIdentity {
    override val name: String get() = "Anonymous ($authenticatedSenderKey)"
    override val publicKey: PublicKey get() = authenticatedSenderKey
    override val isAnonymous: Boolean = true
}
