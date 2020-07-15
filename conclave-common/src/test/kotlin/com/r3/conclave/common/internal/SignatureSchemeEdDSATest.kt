package com.r3.conclave.common.internal

import org.assertj.core.api.Assertions.assertThatExceptionOfType
import org.junit.jupiter.api.Test
import java.security.SignatureException
import java.util.*

class SignatureSchemeEdDSATest {
    @Test
    fun `sign and verify`() {
        val eddsa = SignatureSchemeEdDSA()
        val keyPair = eddsa.generateKeyPair()
        val input = ByteArray(128).also { Random().nextBytes(it) }
        val signature = eddsa.sign(keyPair.private, input)
        val encodedKey = keyPair.public.encoded
        eddsa.verify(eddsa.decodePublicKey(encodedKey), signature, input)

        input[0] = ((input[0] + 1) % 256).toByte()

        assertThatExceptionOfType(SignatureException::class.java).isThrownBy {
            eddsa.verify(eddsa.decodePublicKey(encodedKey), signature, input)
        }
    }
}
