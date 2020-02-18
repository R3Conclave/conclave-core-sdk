package com.r3.conclave.host.internal

import com.r3.sgx.core.common.Cursor
import com.r3.sgx.core.common.SgxSignedQuote
import com.r3.sgx.testing.MockAttestationCertStore
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import kotlin.random.Random

class MockAttestationServiceTest {
    private val attestationService = MockAttestationService()

    @Test
    fun `basic test`() {
        val signedQuote = Cursor(SgxSignedQuote(500), Random.nextBytes(500))
        val response = attestationService.requestSignature(signedQuote)
        val report = response.verify(MockAttestationCertStore.loadTestPkix())
        assertThat(report.isvEnclaveQuoteBody).isEqualTo(signedQuote[signedQuote.encoder.quote])
        assertThat(report.isvEnclaveQuoteStatus).isEqualTo(QuoteStatus.OK)
    }
}
