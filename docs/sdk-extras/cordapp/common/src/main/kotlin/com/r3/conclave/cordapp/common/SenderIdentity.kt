package com.r3.conclave.cordapp.common

import java.security.PublicKey

/**
 * Defines the interface accessible to the sender and the Enclave to create the [SenderIdentity] instance and to
 * query the sender's identity properties. This can always be used by the Enclave to uniquely identify the sender,
 * even in the case the sender did not send its verifiable identity. In anonymous mode the identity can be used
 * to uniquely identify within an active Enclave encrypted session or between sessions while the encryption key
 * remains the same.
 */
interface SenderIdentity {

    /**
     * The verified subject name of the sender or an anonymous identifier if the party did not send its identity
     */
    val name: String

    /**
     * The verified public key of the sender's X509 subject, or the encrypted public key of the enclave session if
     * the party did not send its identity
     */
    val publicKey: PublicKey

    /**
     * A flag for the Enclave indicating whether this is a verified sender identity or an anonymous identity.
     */
    val isAnonymous: Boolean
}
