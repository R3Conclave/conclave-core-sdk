package com.r3.conclave.enclave

import com.r3.conclave.mail.InternalAbstractPostOffice
import com.r3.conclave.mail.Curve25519PublicKey
import com.r3.conclave.mail.PostOffice
import java.security.PublicKey

/**
 * A special post office which is tailored for use inside the enclave. A cached instance can be retrieved using
 * one of the [Enclave.postOffice] methods. The sender private key is automatically set to the enclave's private
 * encryption key, which is why a private key is not required.
 *
 * [EnclavePostOffice] differs from the general [PostOffice] by not having a decrypt method as the enclave already does
 * that, and not exposing the sender private key as that's a secret of the enclave that should not be leaked.
 *
 * @see [PostOffice]
 * @see [Enclave.postOffice]
 */
abstract class EnclavePostOffice(final override val destinationPublicKey: PublicKey, topic: String) : InternalAbstractPostOffice(topic) {
    init {
        // This is a runtime check so we can switch to JDK11+ types later without breaking our own API.
        require(destinationPublicKey is Curve25519PublicKey) {
            "At this time only Conclave originated Curve25519 public keys may be used."
        }
    }
}
