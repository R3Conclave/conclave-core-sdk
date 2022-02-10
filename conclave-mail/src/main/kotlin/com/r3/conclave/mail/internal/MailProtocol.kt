package com.r3.conclave.mail.internal

// For now we have one handshake. Any new ones will have to use the same ciphers. Adding new ciphers won't be forwards
// compatible. The justification is as follows. Curve25519, AESGCM and SHA256 are by this point mature,
// well tested algorithms that are widely deployed to protect all internet traffic. Although new ciphers
// are regularly designed, in practice few are every deployed because elliptic curve crypto with AES and SHA2
// have no known problems, there are none on the horizon beyond quantum computers, and the places where other
// algorithms get deployed tend to be devices with severe power or space constraints.
//
// What of QC? Noise does support a potential post-quantum algorithm. However, no current PQ ciphersuite has
// been settled on by standards bodies yet, and there are multiple competing proposals, many of which have
// worse performance or other problems compared to the standard algorithms. Given that the QCs being built
// at the moment are said to be unusable for breaking encryption keys, and it's unclear when - if ever - such
// a machine will actually be built, it makes sense to wait for PQ standardisation and then implement the
// winning algorithms at that time, rather than attempt to second guess and end up with (relatively speaking)
// not well reviewed algorithms baked into the protocol.
enum class MailProtocol(
    val noiseProtocolName: String,
    /**
     * Returns how many bytes a Noise handshake for the given protocol name requires. These numbers come from the sizes
     * needed for the ephemeral Curve25519 public keys, AES/GCM MAC tags and encrypted static keys.
     *
     * For example: 48 == 32 (pubkey) + 16 (mac of an empty encrypted block)
     *
     * When no payload is specified Noise uses encryptions of the empty block (i.e. only the authentication hash tag is
     * emitted) as a way to authenticate the handshake so far.
     */
    val handshakeLength: Int
) {
    SENDER_KEY_TRANSMITTED("Noise_X_25519_AESGCM_SHA256", 96),

    /**
     * Identical to SENDER_KEY_TRANSMITTED but also supports the private header, an encrypted block that appears in the
     * stream immediately following the handshake.
     *
     * MailDecryptionStream instances supporting this protocol will also accept mail items using the previous one
     * (SENDER_KEY_TRANSMITTED). When doing so, the private header will be null.
     *
     * We add additional byte to the handshake length for compatibility with browsers.
     */
    SENDER_KEY_TRANSMITTED_V2("Noise_X_25519_AESGCM_SHA256", 96 + 1),
}
