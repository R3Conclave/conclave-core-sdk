package com.r3.conclave.mail.internal

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import kotlin.random.Random

class EnclaveMailHeaderImplTests {
    @Test
    fun encodeDecode() {
        val h1 = EnclaveMailHeaderImpl(123, "topic", "from", byteArrayOf(1, 2, 3), Random.nextBytes(16))
        val h2 = EnclaveMailHeaderImpl.decode(h1.encoded)
        assertEquals(h1, h2)
        assertEquals(h1.hashCode(), h2.hashCode())
    }
}
