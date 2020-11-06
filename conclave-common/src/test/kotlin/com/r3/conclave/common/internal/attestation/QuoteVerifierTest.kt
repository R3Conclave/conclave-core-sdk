package com.r3.conclave.common.internal.attestation

import com.r3.conclave.common.EnclaveInstanceInfo
import com.r3.conclave.common.OpaqueBytes
import com.r3.conclave.common.internal.Cursor
import com.r3.conclave.common.internal.EnclaveInstanceInfoImpl
import com.r3.conclave.common.internal.SgxQuote
import com.r3.conclave.common.internal.SgxQuote.signType
import com.r3.conclave.common.internal.SgxQuote.version
import com.r3.conclave.utilities.internal.readFully
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.assertThrows
import java.security.GeneralSecurityException
import kotlin.random.Random

class QuoteVerifierTest {
    companion object {
        fun loadData(): EnclaveInstanceInfoImpl {
            val input = QuoteVerifierTest::class.java.getResourceAsStream("/EnclaveInstanceInfo.ser").readFully()
            return EnclaveInstanceInfo.deserialize(input) as EnclaveInstanceInfoImpl
        }
    }

    @Test
    fun `perfect quote does not fail`() {
        // EnclaveInstanceInfo.deserialize calls QuoteValidator.validate internally
        assertDoesNotThrow { loadData() }
    }

    @Test
    fun `invalid quote version`() {
        val info = loadData()

        val quote = Cursor.wrap(SgxQuote, info.attestationResponse.reportBytes)
        quote[version] = 99

        val message = assertThrows<GeneralSecurityException> {
            QuoteVerifier.verify(
                    quote = quote,
                    signature = info.attestationResponse.signature,
                    collateral = info.attestationResponse.collateral
            )
        }.message
        assertThat(message).contains("UNSUPPORTED_QUOTE_FORMAT")
    }

    @Test
    fun `invalid attestation key type`() {
        val info = loadData()

        val quote = Cursor.wrap(SgxQuote, info.attestationResponse.reportBytes)
        quote[signType] = 99

        val message = assertThrows<GeneralSecurityException> {
            QuoteVerifier.verify(
                    quote = quote,
                    signature = info.attestationResponse.signature,
                    collateral = info.attestationResponse.collateral
            )
        }.message
        assertThat(message).contains("UNSUPPORTED_QUOTE_FORMAT")
    }

    @Test
    fun `bad pck crl issuer`() {
        val info = loadData()
        val badPckCrlCollateral = createBadPckCrlQuoteCollateral(info.attestationResponse.collateral)

        val (status) = QuoteVerifier.verify(
                Cursor.wrap(SgxQuote, info.attestationResponse.reportBytes),
                info.attestationResponse.signature,
                badPckCrlCollateral
        )

        assertEquals(QuoteVerifier.Status.SGX_CRL_UNKNOWN_ISSUER, status)
    }

    @Test
    fun `tcbinfo bad signature`() {
        val info = loadData()
        val good = attestationObjectMapper.readValue(info.attestationResponse.collateral.rawSignedTcbInfo, SignedTcbInfo::class.java)
        val bad = modifyTcbInfo(good, signature = OpaqueBytes(Random.nextBytes(128)))
        val badTcbInfoCollateral = createBadTcbQuoteCollateral(info.attestationResponse.collateral, bad)

        val (status) = QuoteVerifier.verify(
                Cursor.wrap(SgxQuote, info.attestationResponse.reportBytes),
                info.attestationResponse.signature,
                badTcbInfoCollateral
        )

        assertEquals(QuoteVerifier.Status.TCB_INFO_INVALID_SIGNATURE, status)
    }

    @Test
    fun `tcbinfo bad data`() {
        val info = loadData()
        val good = attestationObjectMapper.readValue(info.attestationResponse.collateral.rawSignedTcbInfo, SignedTcbInfo::class.java)
        val bad = modifyTcbInfo(good, pceid = OpaqueBytes.parse("112233445566"))
        val badTcbInfoCollateral = createBadTcbQuoteCollateral(info.attestationResponse.collateral, bad)

        val (status) = QuoteVerifier.verify(
                Cursor.wrap(SgxQuote, info.attestationResponse.reportBytes),
                info.attestationResponse.signature,
                badTcbInfoCollateral
        )

        assertEquals(QuoteVerifier.Status.TCB_INFO_INVALID_SIGNATURE, status)
    }

    private fun modifyTcbInfo(
            good: SignedTcbInfo,
            signature: OpaqueBytes = good.signature,
            fmspc: OpaqueBytes = good.tcbInfo.fmspc,
            pceid: OpaqueBytes = good.tcbInfo.pceId
    ): SignedTcbInfo {
        return SignedTcbInfo(
                tcbInfo = TcbInfo(
                        version = good.tcbInfo.version,
                        issueDate = good.tcbInfo.issueDate,
                        nextUpdate = good.tcbInfo.nextUpdate,
                        fmspc = fmspc,
                        pceId = pceid,
                        tcbType = good.tcbInfo.tcbType,
                        tcbEvaluationDataNumber = good.tcbInfo.tcbEvaluationDataNumber,
                        tcbLevels = good.tcbInfo.tcbLevels
                ),
                signature = signature
        )
    }

    private fun createBadTcbQuoteCollateral(good: QuoteCollateral, badTcbInfo: SignedTcbInfo): QuoteCollateral {
        val json = attestationObjectMapper.writeValueAsString(badTcbInfo)
        return QuoteCollateral(
                version = good.version,
                pckCrlIssuerChain = good.pckCrlIssuerChain,
                rawRootCaCrl = good.rawRootCaCrl,
                rawPckCrl = good.rawPckCrl,
                rawTcbInfoIssuerChain = good.rawTcbInfoIssuerChain,
                rawSignedTcbInfo = json,
                rawQeIdentityIssuerChain = good.rawQeIdentityIssuerChain,
                rawSignedQeIdentity = good.rawSignedQeIdentity
        )
    }

    private fun createBadPckCrlQuoteCollateral(good: QuoteCollateral): QuoteCollateral {
        val badPckCrl = String(javaClass.getResourceAsStream("/sample.root.crl.pem").readFully())
        return QuoteCollateral(
                version = good.version,
                pckCrlIssuerChain = good.pckCrlIssuerChain,
                rawRootCaCrl = good.rawRootCaCrl,
                rawPckCrl = badPckCrl,
                rawTcbInfoIssuerChain = good.rawTcbInfoIssuerChain,
                rawSignedTcbInfo = good.rawSignedTcbInfo,
                rawQeIdentityIssuerChain = good.rawQeIdentityIssuerChain,
                rawSignedQeIdentity = good.rawSignedQeIdentity
        )
    }
}
