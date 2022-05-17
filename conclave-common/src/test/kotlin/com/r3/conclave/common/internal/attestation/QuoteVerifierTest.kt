package com.r3.conclave.common.internal.attestation

import com.r3.conclave.common.EnclaveInstanceInfo
import com.r3.conclave.common.OpaqueBytes
import com.r3.conclave.common.internal.Cursor
import com.r3.conclave.common.internal.EnclaveInstanceInfoImpl
import com.r3.conclave.common.internal.SgxQuote.signType
import com.r3.conclave.common.internal.SgxQuote.version
import com.r3.conclave.common.internal.SgxSignedQuote
import com.r3.conclave.common.internal.SgxSignedQuote.quote
import com.r3.conclave.utilities.internal.readFully
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.random.Random

class QuoteVerifierTest {
    companion object {
        fun loadSampleDcapAttestation(iceLake: Boolean = false): DcapAttestation {
            val input = QuoteVerifierTest::class.java.getResourceAsStream(
                if (iceLake)
                    "EnclaveInstanceInfo-DCAP-IceLake.ser"
                else
                    "EnclaveInstanceInfo-DCAP.ser"
            ).readFully()
            return (EnclaveInstanceInfo.deserialize(input) as EnclaveInstanceInfoImpl).attestation as DcapAttestation
        }
    }

    @Test
    fun `perfect quote does not fail`() {
        // EnclaveInstanceInfo.deserialize calls QuoteValidator.validate internally
        val (signedQuote, collateral) = loadSampleDcapAttestation(iceLake = false)
        val (verificationStatus) = QuoteVerifier.verify(
            signedQuote = signedQuote,
            collateral = collateral
        )
        assertThat(verificationStatus).isEqualTo(TcbStatus.UpToDate)
    }

    @Test
    fun `perfect quote does not fail for IceLake`() {
        // EnclaveInstanceInfo.deserialize calls QuoteValidator.validate internally
        val (signedQuote, collateral) = loadSampleDcapAttestation(iceLake = true)
        val (verificationStatus) = QuoteVerifier.verify(
            signedQuote = signedQuote,
            collateral = collateral
        )
        assertThat(verificationStatus).isEqualTo(TcbStatus.UpToDate)
    }

    @Test
    fun `invalid quote version`() {
        val (signedQuote, collateral) = loadSampleDcapAttestation()

        val invalidSignedQuote = Cursor.wrap(SgxSignedQuote, signedQuote.bytes)
        invalidSignedQuote[quote][version] = 99

        val (verificationStatus) = QuoteVerifier.verify(
            signedQuote = invalidSignedQuote,
            collateral = collateral
        )
        assertThat(verificationStatus).isEqualTo(QuoteVerifier.ErrorStatus.UNSUPPORTED_QUOTE_FORMAT)
    }

    @Test
    fun `invalid attestation key type`() {
        val (signedQuote, collateral) = loadSampleDcapAttestation()

        val invalidSignedQuote = Cursor.wrap(SgxSignedQuote, signedQuote.bytes)
        invalidSignedQuote[quote][signType] = 99

        val message = assertThrows<IllegalStateException> {
            QuoteVerifier.verify(
                signedQuote = invalidSignedQuote,
                collateral = collateral
            )
        }.message
        assertThat(message).contains("Not a ECDSA-256-with-P-256 auth data.")
    }

    @Test
    fun `bad pck crl issuer`() {
        val (signedQuote, collateral) = loadSampleDcapAttestation()
        val badPckCrlCollateral =
            collateral.copy(rawPckCrl = String(javaClass.getResourceAsStream("sample.root.crl.pem").readFully()))

        val (status) = QuoteVerifier.verify(
            signedQuote,
            badPckCrlCollateral
        )

        assertEquals(QuoteVerifier.ErrorStatus.SGX_CRL_UNKNOWN_ISSUER, status)
    }

    @Test
    fun `tcbinfo bad signature`() {
        val (signedQuote, collateral) = loadSampleDcapAttestation()
        val badSignedTcbInfo = collateral.signedTcbInfo.copy(signature = OpaqueBytes(Random.nextBytes(128)))
        val badTcbInfoCollateral =
            collateral.copy(rawSignedTcbInfo = attestationObjectMapper.writeValueAsString(badSignedTcbInfo))

        val (status) = QuoteVerifier.verify(
            signedQuote,
            badTcbInfoCollateral
        )

        assertEquals(QuoteVerifier.ErrorStatus.TCB_INFO_INVALID_SIGNATURE, status)
    }

    @Test
    fun `tcbinfo bad data`() {
        val (signedQuote, collateral) = loadSampleDcapAttestation()
        val good = collateral.signedTcbInfo
        val bad = good.copy(tcbInfo = good.tcbInfo.copy(pceId = OpaqueBytes.parse("112233445566")))
        val badTcbInfoCollateral = collateral.copy(rawSignedTcbInfo = attestationObjectMapper.writeValueAsString(bad))

        val (status) = QuoteVerifier.verify(
            signedQuote,
            badTcbInfoCollateral
        )

        assertEquals(QuoteVerifier.ErrorStatus.TCB_INFO_INVALID_SIGNATURE, status)
    }
}
