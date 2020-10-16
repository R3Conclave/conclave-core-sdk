package com.r3.conclave.host.internal

import com.r3.conclave.common.EnclaveMode
import com.r3.conclave.common.internal.ByteCursor
import com.r3.conclave.common.internal.Cursor
import com.r3.conclave.common.internal.SgxQuote.version
import com.r3.conclave.common.internal.SgxSignedQuote
import com.r3.conclave.common.internal.SgxSignedQuote.quote
import com.r3.conclave.common.internal.SignatureSchemeEdDSA
import com.r3.conclave.common.internal.attestation.AttestationReport
import com.r3.conclave.internaltesting.createSignedQuote
import com.r3.conclave.mail.Curve25519KeyPairGenerator
import com.r3.conclave.mail.Curve25519PublicKey
import org.assertj.core.api.Assertions.assertThatIllegalStateException
import org.junit.jupiter.api.Test

class AttestationServiceTest {
    private val signingKeyPair = SignatureSchemeEdDSA().generateKeyPair()
    private val encryptionKeyPair = Curve25519KeyPairGenerator().generateKeyPair()
    private val signedQuote = createSignedQuote(dataSigningKey = signingKeyPair.public, encryptionKey = encryptionKeyPair.public)

    @Test
    fun `signed quote in the response is different`() {
        val differentSignedQuote = signedQuote.copy {
            this[quote][version] = 9
        }

        val attestationService = object : MockAttestationService() {
            override fun modifyReport(report: AttestationReport): AttestationReport {
                return report.copy(isvEnclaveQuoteBody = differentSignedQuote[quote])
            }
        }

        assertThatIllegalStateException().isThrownBy {
            attestationService.doAttest(signingKeyPair.public, encryptionKeyPair.public as Curve25519PublicKey,
                    signedQuote, EnclaveMode.SIMULATION)
        }.withMessage("The quote in the attestation report is not the one that was provided to the attestation service.")
    }

    private fun ByteCursor<SgxSignedQuote>.copy(modify: ByteCursor<SgxSignedQuote>.() -> Unit): ByteCursor<SgxSignedQuote> {
        val copy = Cursor.wrap(SgxSignedQuote, bytes)
        modify(copy)
        return copy
    }
}
