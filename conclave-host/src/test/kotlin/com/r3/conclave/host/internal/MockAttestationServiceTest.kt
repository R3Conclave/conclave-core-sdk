package com.r3.conclave.host.internal

import com.r3.conclave.common.internal.Cursor
import com.r3.conclave.common.internal.SgxSignedQuote
import com.r3.conclave.common.internal.attestation.AttestationParameters
import com.r3.conclave.common.internal.attestation.QuoteStatus
import com.r3.conclave.common.internal.quote
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import kotlin.random.Random

class MockAttestationServiceTest {
    private val attestationService = MockAttestationService()

    @Test
    fun `basic test`() {
        val signedQuote = Cursor(SgxSignedQuote(500), Random.nextBytes(500))
        val report = attestationService.requestSignature(signedQuote).verify(AttestationParameters.MOCK)
        assertThat(report.isvEnclaveQuoteBody).isEqualTo(signedQuote.quote)
        assertThat(report.isvEnclaveQuoteStatus).isEqualTo(QuoteStatus.OK)
    }
}
