package com.r3.conclave.common.internal

import com.r3.conclave.common.*
import com.r3.conclave.common.internal.SgxQuote.reportBody
import com.r3.conclave.common.internal.SgxReportBody.reportData
import com.r3.conclave.common.internal.attestation.AttestationReport
import com.r3.conclave.common.internal.attestation.QuoteStatus
import com.r3.conclave.host.internal.MockAttestationService
import com.r3.conclave.testing.createSignedQuote
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatIllegalArgumentException
import org.junit.jupiter.api.Test
import java.nio.ByteBuffer
import java.time.Instant
import kotlin.random.Random

class EnclaveInstanceInfoImplTest {
    private val signingKeyPair = SignatureSchemeEdDSA().generateKeyPair()
    private val measurement = SHA256Hash.wrap(Random.nextBytes(32))
    private val cpuSvn = OpaqueBytes(Random.nextBytes(16))
    private val mrsigner = SHA256Hash.wrap(Random.nextBytes(32))
    private val isvProdId = 65535  // Max allowed product ID
    private val isvSvn = 65535   // Max allowed ISVSVN
    private val signedQuote = createSignedQuote(
            cpuSvn = cpuSvn,
            measurement = measurement,
            mrsigner = mrsigner,
            isvProdId = isvProdId,
            isvSvn = isvSvn,
            dataSigningKey = signingKeyPair.public
    )

    private var reportQuoteStatus: QuoteStatus = QuoteStatus.OK
    private var reportTimestamp: Instant = Instant.now()

    private val attestationService = object : MockAttestationService() {
        override fun modifyReport(report: AttestationReport): AttestationReport {
            return report.copy(isvEnclaveQuoteStatus = reportQuoteStatus, timestamp = reportTimestamp)
        }
    }

    @Test
    fun `report data is not the signing key hash`() {
        val containingInvalidReportData = signedQuote.copy {
            quote[reportBody][reportData] = ByteBuffer.wrap(Random.nextBytes(64))
        }
        assertThatIllegalArgumentException().isThrownBy {
            newInstance(signedQuote = containingInvalidReportData)
        }.withMessage("The report data of the quote does not equal the SHA-512 hash of the data signing key.")
    }

    @Test
    fun `verifier is able to verify signature`() {
        val message = Random.nextBytes(100)
        val signature = SignatureSchemeEdDSA.createSignature().run {
            initSign(signingKeyPair.private)
            update(message)
            sign()
        }
        val verifier = newInstance().verifier()
        verifier.update(message)
        assertThat(verifier.verify(signature)).isTrue()
    }

    @Test
    fun enclaveInfo() {
        val enclaveInfo = newInstance().enclaveInfo
        assertThat(enclaveInfo.codeHash).isEqualTo(measurement)
        assertThat(enclaveInfo.codeSigningKeyHash).isEqualTo(mrsigner)
        assertThat(enclaveInfo.productID).isEqualTo(isvProdId)
        assertThat(enclaveInfo.revocationLevel).isEqualTo(isvSvn - 1)
        assertThat(enclaveInfo.enclaveMode).isEqualTo(EnclaveMode.SIMULATION)
    }

    @Test
    fun securityInfo() {
        val securityInfo = newInstance().securityInfo as SGXEnclaveSecurityInfo
        assertThat(securityInfo.timestamp).isEqualTo(reportTimestamp)
        assertThat(securityInfo.cpuSVN).isEqualTo(cpuSvn)
    }

    @Test
    fun `serialisation round-trip`() {
        reportQuoteStatus = QuoteStatus.GROUP_OUT_OF_DATE
        val original = newInstance()
        val deserialised = EnclaveInstanceInfo.deserialize(original.serialize()) as EnclaveInstanceInfoImpl
        assertThat(deserialised.dataSigningKey).isEqualTo(original.dataSigningKey)
        assertThat(deserialised.attestationResponse.reportBytes).isEqualTo(original.attestationResponse.reportBytes)
        assertThat(deserialised.attestationResponse.signature).isEqualTo(original.attestationResponse.signature)
        assertThat(deserialised.attestationResponse.certPath).isEqualTo(original.attestationResponse.certPath)
        assertThat(deserialised.enclaveMode).isEqualTo(original.enclaveMode)
    }

    private fun ByteCursor<SgxSignedQuote>.copy(modify: ByteCursor<SgxSignedQuote>.() -> Unit): ByteCursor<SgxSignedQuote> {
        val copy = Cursor(SgxSignedQuote(encoder.size()), readBytes())
        modify(copy)
        return copy
    }

    private fun newInstance(signedQuote: ByteCursor<SgxSignedQuote> = this.signedQuote): EnclaveInstanceInfoImpl {
        return attestationService.doAttest(signingKeyPair.public, signedQuote, EnclaveMode.SIMULATION)
    }
}
