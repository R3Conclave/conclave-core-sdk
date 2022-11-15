package com.r3.conclave.integrationtests.general.tests.plugin

import org.assertj.core.api.Assertions.assertThat
import org.bouncycastle.openssl.PEMKeyPair
import org.bouncycastle.openssl.PEMParser
import org.bouncycastle.openssl.jcajce.JcaPEMKeyConverter
import org.junit.jupiter.api.Test
import java.security.interfaces.RSAPublicKey
import kotlin.io.path.deleteExisting
import kotlin.io.path.reader

class CreateDummyKeyTest : AbstractConclaveTaskTest() {
    override val taskName: String get() = "createDummyKey"
    override val outputName: String get() = "dummy_key.pem"
    /**
     * The dummy key is meant to be random.
     */
    override val isReproducible: Boolean get() = false

    @Test
    fun `output is an RSA key pair in PEM format with public exponent of 3`() {
        runTask()
        assertThat(dummyKey().publicExponent).isEqualTo(3)
    }

    @Test
    fun `key is random`() {
        val dummyModuli = Array(10) {
            runTask()
            val key = dummyKey()
            output.deleteExisting()
            key.modulus
        }
        assertThat(dummyModuli).doesNotHaveDuplicates()
    }

    private fun dummyKey(): RSAPublicKey {
        return PEMParser(output.reader()).use { pem ->
            JcaPEMKeyConverter().getKeyPair(pem.readObject() as PEMKeyPair).public as RSAPublicKey
        }
    }
}
