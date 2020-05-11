package com.r3.conclave.common.internal

import com.r3.conclave.common.*
import com.r3.conclave.common.internal.SgxAttributes.flags
import com.r3.conclave.common.internal.SgxQuote.reportBody
import com.r3.conclave.common.internal.SgxQuote.version
import com.r3.conclave.common.internal.SgxReportBody.attributes
import com.r3.conclave.common.internal.SgxReportBody.reportData
import com.r3.conclave.common.internal.attestation.AttestationReport
import com.r3.conclave.common.internal.attestation.AttestationResponse
import com.r3.conclave.common.internal.attestation.QuoteStatus
import com.r3.conclave.host.internal.MockAttestationService
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
    private val signedQuote = Cursor.allocate(SgxSignedQuote(500)).apply {
        quote[reportBody].apply {
            this[reportData] = SHA512Hash.hash(signingKeyPair.public.encoded).buffer()
            this[SgxReportBody.cpuSvn] = cpuSvn.buffer()
            this[SgxReportBody.measurement] = measurement.buffer()
            this[SgxReportBody.mrsigner] = mrsigner.buffer()
            this[SgxReportBody.isvProdId] = isvProdId
            this[SgxReportBody.isvSvn] = isvSvn
            this[attributes][flags] = SgxEnclaveFlags.DEBUG
        }
    }

    private var reportQuoteStatus: QuoteStatus = QuoteStatus.OK
    private var reportTimestamp: Instant = Instant.now()

    private val attestationService = object : MockAttestationService() {
        override fun modifyReport(report: AttestationReport): AttestationReport {
            return report.copy(isvEnclaveQuoteStatus = reportQuoteStatus, timestamp = reportTimestamp)
        }
    }

    @Test
    fun `signed quote is different to the one in the attestation report`() {
        val differentSignedQuote = signedQuote.copy {
            quote[version] = 9
        }
        assertThatIllegalArgumentException().isThrownBy {
            newInstance(signedQuote = differentSignedQuote, attestationResponse = attestationService.requestSignature(signedQuote))
        }.withMessage("The quote in the attestation report is not the one that was provided for attestation.")
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
        assertThat(deserialised.signedQuote.read()).isEqualTo(original.signedQuote.read())
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

    private fun newInstance(
            signedQuote: ByteCursor<SgxSignedQuote> = this.signedQuote,
            attestationResponse: AttestationResponse = attestationService.requestSignature(signedQuote)
    ): EnclaveInstanceInfoImpl {
        return EnclaveInstanceInfoImpl(signingKeyPair.public, signedQuote, attestationResponse, EnclaveMode.SIMULATION)
    }
}
