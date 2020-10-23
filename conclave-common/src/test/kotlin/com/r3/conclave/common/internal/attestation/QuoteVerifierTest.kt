package com.r3.conclave.common.internal.attestation

import com.fasterxml.jackson.databind.ObjectMapper
import com.r3.conclave.common.EnclaveInstanceInfo
import com.r3.conclave.common.internal.EnclaveInstanceInfoImpl
import com.r3.conclave.utilities.internal.readFully
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.assertThrows
import java.security.GeneralSecurityException
import java.security.Signature
import java.security.cert.CertPath
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.time.Instant
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

        val badVersion: Byte = 99
        info.attestationResponse.reportBytes[0] = badVersion // quote version is Int16 at offset 0

        val message = assertThrows<GeneralSecurityException> {
            QuoteVerifier.verify(
                reportBytes = info.attestationResponse.reportBytes,
                signature = info.attestationResponse.signature,
                certPath = info.attestationResponse.certPath,
                collateral = info.attestationResponse.collateral
            )
        }.message
        assertThat(message).contains("UNSUPPORTED_QUOTE_FORMAT")
    }

    @Test
    fun `invalid attestation key type`() {
        val info = loadData()

        val badKeyType: Byte = 99
        info.attestationResponse.reportBytes[2] = badKeyType // key type is Int16 at offset 2

        val message = assertThrows<GeneralSecurityException> {
            QuoteVerifier.verify(
                reportBytes = info.attestationResponse.reportBytes,
                signature = info.attestationResponse.signature,
                certPath = info.attestationResponse.certPath,
                collateral = info.attestationResponse.collateral
            )
        }.message
        assertThat(message).contains("UNSUPPORTED_QUOTE_FORMAT")
    }

    @Test
    fun `bad pck cert path - too short`() {
        val info = loadData()
        val badCertPath = createBadCertPath()

        val (status, latestIssueDate, collateralExpired) = QuoteVerifier.verify(ByteArray(0), ByteArray(0), badCertPath,
                info.attestationResponse.collateral)

        assertEquals(QuoteVerifier.Status.UNSUPPORTED_CERT_FORMAT, status)
    }

    @Test
    fun `bad pck crl issuer`() {
        val info = loadData()
        val badPckCrlCollateral = createBadPckCrlQuoteCollateral(info.attestationResponse.collateral)

        val (status, latestIssueDate, collateralExpired) = QuoteVerifier.verify(ByteArray(0), ByteArray(0),
            info.attestationResponse.certPath, badPckCrlCollateral
        )

        assertEquals(QuoteVerifier.Status.SGX_CRL_UNKNOWN_ISSUER, status)
    }

    @Test
    fun `tcbinfo bad signature`() {
        val randomSignature = generateRandomHexString(128)

        val info = loadData()
        val good = attestationObjectMapper.readValue(info.attestationResponse.collateral.tcbInfo, TcbInfoSigned::class.java)
        val bad = modifyTcbInfo(good, signature = randomSignature);
        val badTcbInfoCollateral = createBadTcbQuoteCollateral(info.attestationResponse.collateral, bad)

        val (status, latestIssueDate, collateralExpired) = QuoteVerifier.verify(ByteArray(0), ByteArray(0),
                info.attestationResponse.certPath, badTcbInfoCollateral
            )

        assertEquals(QuoteVerifier.Status.TCB_INFO_INVALID_SIGNATURE, status)
    }

    private fun generateRandomHexString(size: Int): String {
        val charPool: List<Char> = ('a'..'f') + ('0'..'9')
        val randomSignature = (1..size)
                .map { i -> Random.nextInt(0, charPool.size) }
                .map(charPool::get)
                .joinToString("");
        return randomSignature
    }

    @Test
    fun `tcbinfo bad data`() {
        val info = loadData()
        val good = attestationObjectMapper.readValue(info.attestationResponse.collateral.tcbInfo, TcbInfoSigned::class.java)
        val bad = modifyTcbInfo(good, pceid = "112233445566")
        val badTcbInfoCollateral = createBadTcbQuoteCollateral(info.attestationResponse.collateral, bad)

        val (status, latestIssueDate, collateralExpired) = QuoteVerifier.verify(ByteArray(0), ByteArray(0),
                info.attestationResponse.certPath, badTcbInfoCollateral
            )

        assertEquals(QuoteVerifier.Status.TCB_INFO_INVALID_SIGNATURE, status)
    }

    private fun modifyTcbInfo(good: TcbInfoSigned, signature: String? = null, fmspc: String? = null, pceid: String? = null): TcbInfoSigned {
        return TcbInfoSigned(
            tcbInfo = TcbInfo(
                version = good.tcbInfo.version,
                issueDate = good.tcbInfo.issueDate,
                nextUpdate = good.tcbInfo.nextUpdate,
                fmspc = fmspc ?: good.tcbInfo.fmspc,
                pceId = pceid ?: good.tcbInfo.pceId,
                tcbType = good.tcbInfo.tcbType,
                tcbEvaluationDataNumber = good.tcbInfo.tcbEvaluationDataNumber,
                tcbLevels = good.tcbInfo.tcbLevels
            ),
            signature = signature ?: good.signature
        )
    }
    private fun createBadCertPath(): CertPath {
        val cert = getNonSGXCert()
        val cf = CertificateFactory.getInstance("X.509")
        return cf.generateCertPath(listOf(cert))
    }

    private fun createBadTcbQuoteCollateral(good: QuoteCollateral, badTcbInfo: TcbInfoSigned): QuoteCollateral {
        val json = attestationObjectMapper.writeValueAsString(badTcbInfo)
        return QuoteCollateral(
                version = good.version,
                pckCrlIssuerChain = good.pckCrlIssuerChain,
                rootCaCrl = good.rootCaCrl,
                pckCrl = good.pckCrl,
                tcbInfoIssuerChain = good.tcbInfoIssuerChain,
                tcbInfo = json,
                qeIdentityIssuerChain = good.qeIdentityIssuerChain,
                qeIdentity = good.qeIdentity
        )
    }

    private fun createBadPckCrlQuoteCollateral(good: QuoteCollateral): QuoteCollateral {
        val badPckCrl = String(javaClass.getResourceAsStream("/sample.root.crl.pem").readFully())
        return QuoteCollateral(
                version = good.version,
                pckCrlIssuerChain = good.pckCrlIssuerChain,
                rootCaCrl = good.rootCaCrl,
                pckCrl = badPckCrl,
                tcbInfoIssuerChain = good.tcbInfoIssuerChain,
                tcbInfo = good.tcbInfo,
                qeIdentityIssuerChain = good.qeIdentityIssuerChain,
                qeIdentity = good.qeIdentity
        )
    }

    private fun getNonSGXCert(): X509Certificate {
        val cf = CertificateFactory.getInstance("X.509")
        return cf.generateCertificate(javaClass.getResourceAsStream("/r3_pck_cert.pem")) as X509Certificate
    }
}
