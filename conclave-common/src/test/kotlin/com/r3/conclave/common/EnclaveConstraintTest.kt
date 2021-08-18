package com.r3.conclave.common

import com.r3.conclave.common.EnclaveSecurityInfo.Summary.*
import com.r3.conclave.mail.PostOffice
import org.assertj.core.api.Assertions.*
import org.junit.jupiter.api.Test
import java.security.PrivateKey
import java.security.PublicKey
import java.security.Signature
import java.time.Instant
import java.time.Period
import kotlin.random.Random

class EnclaveConstraintTest {
    companion object {
        private val codeHash = randomSha256()
        private val codeSigner = randomSha256()
        private const val productId = 10
        private const val revocationLevel = 3

        private fun randomSha256() = SHA256Hash.wrap(Random.nextBytes(32))
    }

    @Test
    fun defaults() {
        val default = EnclaveConstraint()
        assertThat(default.acceptableCodeHashes).isEmpty()
        assertThat(default.acceptableSigners).isEmpty()
        assertThat(default.productID).isNull()
        assertThat(default.minRevocationLevel).isNull()
        assertThat(default.minSecurityLevel).isEqualTo(STALE)
    }

    @Test
    fun `parse one code hash`() {
        val actual = EnclaveConstraint.parse("C:da78b28564fe5a9b4f5912901f068f9f94483006bc53e10ab93dbb97675eba26")
        val expected = EnclaveConstraint().apply {
            acceptableCodeHashes =
                mutableSetOf(SHA256Hash.parse("da78b28564fe5a9b4f5912901f068f9f94483006bc53e10ab93dbb97675eba26"))
        }
        assertThat(actual).isEqualTo(expected)
        assertThat(EnclaveConstraint.parse(actual.toString())).isEqualTo(expected)
    }

    @Test
    fun `parse one code signer`() {
        val actual =
            EnclaveConstraint.parse("PROD:10 S:da78b28564fe5a9b4f5912901f068f9f94483006bc53e10ab93dbb97675eba26")
        val expected = EnclaveConstraint().apply {
            acceptableSigners =
                mutableSetOf(SHA256Hash.parse("da78b28564fe5a9b4f5912901f068f9f94483006bc53e10ab93dbb97675eba26"))
            productID = 10
        }
        assertThat(actual).isEqualTo(expected)
        assertThat(EnclaveConstraint.parse(actual.toString())).isEqualTo(expected)
    }

    @Test
    fun `parse multiple code hashes and code signers`() {
        val actual = EnclaveConstraint.parse(
            "C:da78b28564fe5a9b4f5912901f068f9f94483006bc53e10ab93dbb97675eba26 " +
                    "S:edd5c98fcb260633a6e6abd0aaa59c07aee6e2492fe22fb89413edd875edf27a " +
                    "C:0f738309dee76fcd9ad1b50f1c208ad3b1d2c8a62059c410b4806a88451c66ee " +
                    "S:f77e0720c4dd119395e195223908d0e98baeca678bf9af8cabea2cc5278dac8d " +
                    "PROD:3123"
        )
        val expected = EnclaveConstraint().apply {
            acceptableCodeHashes = mutableSetOf(
                SHA256Hash.parse("0f738309dee76fcd9ad1b50f1c208ad3b1d2c8a62059c410b4806a88451c66ee"),
                SHA256Hash.parse("da78b28564fe5a9b4f5912901f068f9f94483006bc53e10ab93dbb97675eba26")
            )
            acceptableSigners = mutableSetOf(
                SHA256Hash.parse("edd5c98fcb260633a6e6abd0aaa59c07aee6e2492fe22fb89413edd875edf27a"),
                SHA256Hash.parse("f77e0720c4dd119395e195223908d0e98baeca678bf9af8cabea2cc5278dac8d")
            )
            productID = 3123
        }
        assertThat(actual).isEqualTo(expected)
        assertThat(EnclaveConstraint.parse(actual.toString())).isEqualTo(expected)
    }

    @Test
    fun `parse minimum revocation level`() {
        val actual =
            EnclaveConstraint.parse("REVOKE:3 C:da78b28564fe5a9b4f5912901f068f9f94483006bc53e10ab93dbb97675eba26")
        val expected = EnclaveConstraint().apply {
            minRevocationLevel = 3
            acceptableCodeHashes =
                mutableSetOf(SHA256Hash.parse("da78b28564fe5a9b4f5912901f068f9f94483006bc53e10ab93dbb97675eba26"))
        }
        assertThat(actual).isEqualTo(expected)
        assertThat(EnclaveConstraint.parse(actual.toString())).isEqualTo(expected)
    }

    @Test
    fun `parse minimum security level`() {
        val actual =
            EnclaveConstraint.parse("SEC:SECURE C:da78b28564fe5a9b4f5912901f068f9f94483006bc53e10ab93dbb97675eba26")
        val expected = EnclaveConstraint().apply {
            minSecurityLevel = SECURE
            acceptableCodeHashes =
                mutableSetOf(SHA256Hash.parse("da78b28564fe5a9b4f5912901f068f9f94483006bc53e10ab93dbb97675eba26"))
        }
        assertThat(actual).isEqualTo(expected)
        assertThat(EnclaveConstraint.parse(actual.toString())).isEqualTo(expected)
    }

    @Test
    fun `parse maximum attestation age`() {
        val actual =
            EnclaveConstraint.parse("EXPIRE:P2M3W4D C:da78b28564fe5a9b4f5912901f068f9f94483006bc53e10ab93dbb97675eba26")
        val expected = EnclaveConstraint().apply {
            maxAttestationAge = Period.parse("P2M3W4D")
            acceptableCodeHashes =
                mutableSetOf(SHA256Hash.parse("da78b28564fe5a9b4f5912901f068f9f94483006bc53e10ab93dbb97675eba26"))
        }
        assertThat(actual.maxAttestationAge).isNotNull
        assertThat(actual).isEqualTo(expected)
        assertThat(EnclaveConstraint.parse(actual.toString())).isEqualTo(expected)
    }

    @Test
    fun `invalid product ID`() {
        val constraint = EnclaveConstraint()
        assertThatIllegalArgumentException().isThrownBy {
            constraint.productID = -1
        }.withMessage("Product ID is negative.")
        assertThatIllegalArgumentException().isThrownBy {
            constraint.productID = 65536
        }.withMessage("Product ID is not a 16-bit number.")
    }

    @Test
    fun `invalid min revocation level`() {
        val constraint = EnclaveConstraint()
        assertThatIllegalArgumentException().isThrownBy {
            constraint.minRevocationLevel = -1
        }.withMessage("Min revocation level is negative.")
    }

    @Test
    fun `invalid states`() {
        assertThatCheckThrowsIllegalStateException("Either a code hash or a code signer must be provided.") { }
        assertThatCheckThrowsIllegalStateException("A code signer must be provided with a product ID.") {
            acceptableCodeHashes.add(codeHash)
            productID = 3
        }
        assertThatCheckThrowsIllegalStateException("A product ID must be provided with a code signer.") {
            acceptableSigners.add(codeSigner)
        }
    }

    @Test
    fun `check against code hash`() {
        val enclave = TestEnclaveInstanceInfo(codeHash = codeHash)
        enclave.checkAgainstConstraint {
            acceptableCodeHashes.add(codeHash)
        }
        assertThatCheckThrowsInvalidEnclaveException(
            "Enclave code hash does not match any of the acceptable code hashes.",
            enclave
        ) {
            acceptableCodeHashes.add(randomSha256())
        }
    }

    @Test
    fun `check against code signer`() {
        val enclave = TestEnclaveInstanceInfo(codeSigningKeyHash = codeSigner)
        enclave.checkAgainstConstraint {
            acceptableSigners.add(codeSigner)
            productID = productId
        }
        assertThatCheckThrowsInvalidEnclaveException(
            "Enclave code signer does not match any of the acceptable code signers.",
            enclave
        ) {
            acceptableSigners.add(randomSha256())
            productID = productId
        }
    }

    @Test
    fun `check against either code hash or code signer`() {
        val enclave = TestEnclaveInstanceInfo(codeHash = codeHash, codeSigningKeyHash = codeSigner)
        enclave.checkAgainstConstraint {
            acceptableCodeHashes.add(codeHash)
            acceptableSigners.add(randomSha256())
            productID = productId
        }
        enclave.checkAgainstConstraint {
            acceptableCodeHashes.add(randomSha256())
            acceptableSigners.add(codeSigner)
            productID = productId
        }
        assertThatCheckThrowsInvalidEnclaveException(
            "Enclave does not match any of the acceptable code hashes or code signers.",
            enclave
        ) {
            acceptableCodeHashes.add(randomSha256())
            acceptableSigners.add(randomSha256())
            productID = productId
        }
    }

    @Test
    fun `check against product ID`() {
        val enclave = TestEnclaveInstanceInfo(productID = 10)
        enclave.checkAgainstConstraint {
            acceptableSigners.add(codeSigner)
            productID = 10
        }
        assertThatCheckThrowsInvalidEnclaveException(
            "Enclave has a product ID of 10 which does not match the criteria of 1.",
            enclave
        ) {
            acceptableSigners.add(codeSigner)
            productID = 1
        }
    }

    @Test
    fun `check against min revocation level`() {
        val enclave = TestEnclaveInstanceInfo(revocationLevel = 3)
        listOf(2, 3).forEach { minLevel ->
            enclave.checkAgainstConstraint {
                acceptableCodeHashes.add(codeHash)
                minRevocationLevel = minLevel
            }
        }
        assertThatCheckThrowsInvalidEnclaveException(
            "Enclave has a revocation level of 3 which is lower than the required level of 4.",
            enclave
        ) {
            acceptableCodeHashes.add(codeHash)
            minRevocationLevel = 4
        }
    }

    @Test
    fun `check against min security level`() {
        val staleEnclave = TestEnclaveInstanceInfo(summary = STALE)
        listOf(STALE, INSECURE).forEach { minLevel ->
            staleEnclave.checkAgainstConstraint {
                acceptableCodeHashes.add(codeHash)
                minSecurityLevel = minLevel
            }
        }
        assertThatCheckThrowsInvalidEnclaveException(
            "Enclave has a security level of STALE which is lower than the required level of SECURE.",
            staleEnclave
        ) {
            acceptableCodeHashes.add(codeHash)
            minSecurityLevel = SECURE
        }
    }

    @Test
    fun `check against attestation age`() {
        val reallyOldEnclave = TestEnclaveInstanceInfo(timestamp = Instant.EPOCH)
        assertThatCheckThrowsInvalidEnclaveException(
            "Enclave attestation data is out of date with an age that exceeds P6M.",
            reallyOldEnclave,
        ) {
            acceptableCodeHashes.add(codeHash)
            maxAttestationAge = Period.parse("P6M")
        }
        val reallyNewEnclave = TestEnclaveInstanceInfo(timestamp = Instant.now())
        val constraint = EnclaveConstraint()
        constraint.acceptableCodeHashes.add(codeHash)
        constraint.maxAttestationAge = Period.parse("P6M")
        constraint.check(reallyNewEnclave)
    }

    private fun EnclaveInstanceInfo.checkAgainstConstraint(block: EnclaveConstraint.() -> Unit) {
        val constraint = EnclaveConstraint()
        block(constraint)
        constraint.check(this)
    }

    private fun assertThatCheckThrowsIllegalStateException(message: String, block: EnclaveConstraint.() -> Unit) {
        val enclave = TestEnclaveInstanceInfo()
        val constraint = EnclaveConstraint()
        block(constraint)
        assertThatIllegalStateException().isThrownBy {
            constraint.check(enclave)
        }.withMessage(message)
    }

    private fun assertThatCheckThrowsInvalidEnclaveException(
        message: String,
        enclave: EnclaveInstanceInfo,
        block: EnclaveConstraint.() -> Unit
    ) {
        val constraint = EnclaveConstraint()
        block(constraint)
        assertThatThrownBy { constraint.check(enclave) }
            .isInstanceOf(InvalidEnclaveException::class.java)
            .hasMessageContaining(message)
    }

    private class TestEnclaveInstanceInfo(
            codeHash: SecureHash = Companion.codeHash,
            codeSigningKeyHash: SecureHash = codeSigner,
            productID: Int = productId,
            revocationLevel: Int = Companion.revocationLevel,
            summary: EnclaveSecurityInfo.Summary = STALE,
            timestamp: Instant = Instant.now()
    ) : EnclaveInstanceInfo {
        override val enclaveInfo: EnclaveInfo = EnclaveInfo(
            codeHash = codeHash,
            codeSigningKeyHash = codeSigningKeyHash,
            productID = productID,
            revocationLevel = revocationLevel,
            enclaveMode = EnclaveMode.SIMULATION
        )

        override val securityInfo: EnclaveSecurityInfo = SGXEnclaveSecurityInfo(
            summary = summary,
            reason = "for reasons...",
            timestamp = timestamp,
            cpuSVN = OpaqueBytes(Random.nextBytes(16))
        )

        override val encryptionKey: PublicKey get() = TODO("encryptionKey")
        override val dataSigningKey: PublicKey get() = TODO("dataSigningKey")
        override fun createPostOffice(senderPrivateKey: PrivateKey, topic: String): PostOffice =
            TODO("createPostOffice")

        override fun verifier(): Signature = TODO("verifier")
        override fun serialize(): ByteArray = TODO("serialize")
    }
}
