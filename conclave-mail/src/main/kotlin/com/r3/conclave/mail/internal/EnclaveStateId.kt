package com.r3.conclave.mail.internal

import com.r3.conclave.mail.internal.EnclaveStateId.Companion.SIZE_BYTES
import com.r3.conclave.mail.internal.noise.protocol.Noise
import com.r3.conclave.utilities.internal.readExactlyNBytes
import com.r3.conclave.utilities.internal.toHexString
import java.io.InputStream

// This class is intentionally internal and is designed that way (i.e. there's no defensive copying of the bytes
// property). The only place in the API where the "state ID" is exposed is in PostOffice. However it's only exposed to
// allow the user to restore it. For that purpose exposing just a byte array seems sufficient rather than introducing
// another class into the API.
class EnclaveStateId(val bytes: ByteArray) {
    /** Creates a new random state ID. */
    constructor() : this(ByteArray(SIZE_BYTES).also(Noise::random))

    init {
        require(bytes.size == SIZE_BYTES) { bytes.size }
    }

    override fun equals(other: Any?): Boolean {
        return this === other || other is EnclaveStateId && bytes.contentEquals(other.bytes)
    }

    override fun hashCode(): Int = bytes.contentHashCode()

    override fun toString(): String = "EnclaveStateId(${bytes.toHexString()})"

    companion object {
        const val SIZE_BYTES = 16
    }
}

fun InputStream.readEnclaveStateId(): EnclaveStateId = EnclaveStateId(readExactlyNBytes(SIZE_BYTES))
