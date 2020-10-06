package com.r3.conclave.common.internal.attestation

import com.r3.conclave.common.EnclaveInstanceInfo
import com.r3.conclave.common.internal.EnclaveInstanceInfoImpl
import com.r3.conclave.utilities.internal.readFully
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.assertThrows
import java.security.GeneralSecurityException
import java.security.cert.CertPath
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate

class QuoteVerifierTest {
    companion object {
        fun loadData(): EnclaveInstanceInfoImpl {
            val input = QuoteVerifierTest::class.java.getResourceAsStream("/EnclaveInstanceInfo.ser").readFully()
            return EnclaveInstanceInfo.deserialize(input) as EnclaveInstanceInfoImpl
        }
    }

    @Test
    fun `perfect quote verification does not fail`() {
        val info = loadData()
        assertDoesNotThrow {
            QuoteVerifier.verify(
                    reportBytes = info.attestationResponse.reportBytes,
                    signature = info.attestationResponse.signature,
                    certPath = info.attestationResponse.certPath,
                    collateral = info.attestationResponse.collateral
            )
        }
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
        assertThat(message).contains("Unsupported quote version $badVersion")
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
        assertThat(message).contains("Unsupported quote key type $badKeyType")
    }

    @Test
    fun `pck cert check SGX_PCK_CN_PHRASE`() {
        val info = loadData()
        val badCertPath = createBadCertPath()

        val status = QuoteVerifier.cert_collateral_check(badCertPath, info.attestationResponse.collateral)

        assertEquals(QuoteVerifier.Status.STATUS_INVALID_PCK_CERT, status)
    }

    @Test
    fun `pck crl check SGX_INTERMEDIATE_CN_PHRASE`() {
        val info = loadData()
        val badPckCrlCollateral = createBadPckCrlQuoteCollateral(info.attestationResponse.collateral)

        val status = QuoteVerifier.cert_collateral_check(info.attestationResponse.certPath, badPckCrlCollateral)

        assertEquals(QuoteVerifier.Status.STATUS_INVALID_PCK_CRL, status)
    }

    @Test
    fun `pck crl check RevokedCert`() {
        val info = loadData()
        val revokedPckCertCollateral = createRevokedPckCertQuoteCollateral(info.attestationResponse.collateral)

        val status = QuoteVerifier.cert_collateral_check(createRevokedCertPath(), revokedPckCertCollateral)

        assertEquals(QuoteVerifier.Status.STATUS_PCK_REVOKED, status)
    }

    @Test
    fun `tcbinfo check fmspc`() {
        val info = loadData()
        val tcbInfo = TcbInfoSigned(tcbInfo = TcbInfo(fmspc = "112233445566"))
        val badTcbInfoCollateral = createBadTcbQuoteCollateral(info.attestationResponse.collateral, tcbInfo)

        val status = QuoteVerifier.cert_collateral_check(info.attestationResponse.certPath, badTcbInfoCollateral)

        assertEquals(status, QuoteVerifier.Status.STATUS_TCB_INFO_MISMATCH)
    }

    @Test
    fun `tcbinfo check pceid`() {
        val info = loadData()
        val tcbInfo = TcbInfoSigned(tcbInfo = TcbInfo(pceId = "112233445566"))
        val bad = createBadTcbQuoteCollateral(info.attestationResponse.collateral, tcbInfo)

        val status = QuoteVerifier.cert_collateral_check(info.attestationResponse.certPath, bad)

        assertEquals(QuoteVerifier.Status.STATUS_TCB_INFO_MISMATCH, status)
    }

    @Test
    fun `tcb status check`() {
        val info = loadData()
        val tcbInfo = TcbInfoSigned(tcbInfo = TcbInfo(tcbLevels = listOf(TcbLevel(tcbStatus = "Revoked"))))
        val badCollateral = createBadTcbQuoteCollateral(info.attestationResponse.collateral, tcbInfo)

        val status = QuoteVerifier.cert_collateral_check(info.attestationResponse.certPath, badCollateral)

        assertEquals(QuoteVerifier.Status.STATUS_TCB_INFO_MISMATCH, status)
    }

    private fun createBadCertPath(): CertPath {
        val cert = getNonSGXCert()
        val cf = CertificateFactory.getInstance("X.509")
        return cf.generateCertPath(listOf(cert))
    }

    private fun createRevokedCertPath(): CertPath {
        val cert = getRevokedCert()
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

    private fun createRevokedPckCertQuoteCollateral(good: QuoteCollateral): QuoteCollateral {
        val badPckCrl = String(javaClass.getResourceAsStream("/revoked_pck.crl.pem").readFully())
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

    private fun getRevokedCert(): X509Certificate {
        val cf = CertificateFactory.getInstance("X.509")
        return cf.generateCertificate(javaClass.getResourceAsStream("/revoked_pck_cert.pem")) as X509Certificate
    }
}
