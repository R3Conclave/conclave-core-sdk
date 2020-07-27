package com.r3.conclave.host.internal

import com.r3.conclave.common.EnclaveMode
import com.r3.conclave.common.internal.*
import com.r3.conclave.common.internal.attestation.AttestationReport
import com.r3.conclave.mail.Curve25519KeyPairGenerator
import com.r3.conclave.mail.Curve25519PublicKey
import com.r3.conclave.testing.createSignedQuote
import org.assertj.core.api.Assertions.assertThatIllegalStateException
import org.junit.jupiter.api.Test

class AttestationServiceTest {
    private val signingKeyPair = SignatureSchemeEdDSA().generateKeyPair()
    private val encryptionKeyPair = Curve25519KeyPairGenerator().generateKeyPair()
    private val signedQuote = createSignedQuote(dataSigningKey = signingKeyPair.public, encryptionKey = encryptionKeyPair.public)

    @Test
    fun `signed quote in the response is different`() {
        val differentSignedQuote = signedQuote.copy {
            quote[SgxQuote.version] = 9
        }

        val attestationService = object : MockAttestationService() {
            override fun modifyReport(report: AttestationReport): AttestationReport {
                return report.copy(isvEnclaveQuoteBody = differentSignedQuote.quote)
            }
        }

        assertThatIllegalStateException().isThrownBy {
            attestationService.doAttest(signingKeyPair.public, encryptionKeyPair.public as Curve25519PublicKey,
                    signedQuote, EnclaveMode.SIMULATION)
        }.withMessage("The quote in the attestation report is not the one that was provided to the attestation service.")
    }

    private fun ByteCursor<SgxSignedQuote>.copy(modify: ByteCursor<SgxSignedQuote>.() -> Unit): ByteCursor<SgxSignedQuote> {
        val copy = Cursor(SgxSignedQuote(encoder.size()), readBytes())
        modify(copy)
        return copy
    }
}
