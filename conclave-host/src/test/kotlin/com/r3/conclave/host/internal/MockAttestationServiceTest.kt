package com.r3.conclave.host.internal

import com.r3.conclave.common.internal.Cursor
import com.r3.conclave.common.internal.SgxSignedQuote
import com.r3.conclave.common.internal.SgxSignedQuote.quote
import com.r3.conclave.common.internal.attestation.PKIXParametersFactory
import com.r3.conclave.common.internal.attestation.QuoteStatus
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.Instant

class MockAttestationServiceTest {
    private val attestationService = MockAttestationService()

    @Test
    fun `basic test`() {
        val signedQuote = Cursor.wrap(SgxSignedQuote, ByteArray(SgxSignedQuote.minSize))
        val report = attestationService.requestSignature(signedQuote).verify(PKIXParametersFactory.Mock.create(Instant.now()))
        assertThat(report.isvEnclaveQuoteBody).isEqualTo(signedQuote[quote])
        assertThat(report.isvEnclaveQuoteStatus).isEqualTo(QuoteStatus.OK)
    }
}
