package com.r3.conclave.mail.internal

import com.r3.conclave.mail.Curve25519PublicKey
import com.r3.conclave.mail.internal.noise.protocol.*
import java.io.*
import java.security.PrivateKey

const val MAX_PACKET_PLAINTEXT_LENGTH = Noise.MAX_PACKET_LEN - 16

// The payload defined as the user bytes plus any padding of zeros. It does not include the length of the user
// bytes, even though that is encrypted with the payload.
const val MAX_PACKET_PAYLOAD_LENGTH = MAX_PACKET_PLAINTEXT_LENGTH - 2

// Utils for encoding a 16 bit unsigned value in big endian.
fun ByteArray.writeShort(offset: Int, value: Int) {
    this[offset] = (value shr 8).toByte()
    this[offset + 1] = value.toByte()
}

fun OutputStream.writeShort(value: Int) {
    write((value ushr 8) and 0xFF)
    write(value and 0xFF)
}

fun ByteArray.readShort(offset: Int): Int {
    val b1 = this[offset].toInt() and 0xFF
    val b2 = this[offset + 1].toInt() and 0xFF
    return (b1 shl 8) or b2
}

fun OutputStream.writeInt(value: Int) {
    write((value ushr 24) and 0xFF)
    write((value ushr 16) and 0xFF)
    write((value ushr 8) and 0xFF)
    write(value and 0xFF)
}

fun InputStream.readInt(): Int {
    val b1 = (read() and 0xFF)
    val b2 = (read() and 0xFF)
    val b3 = (read() and 0xFF)
    val b4 = (read() and 0xFF)
    return (b1 shl 24) or (b2 shl 16) or (b3 shl 8) or b4
}

fun privateCurve25519KeyToPublic(privateKey: PrivateKey): Curve25519PublicKey {
    val dh: DHState = Noise.createDH("25519")
    dh.setPrivateKey(privateKey.encoded, 0)
    return Curve25519PublicKey(dh.publicKey)
}
