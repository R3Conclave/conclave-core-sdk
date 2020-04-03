package com.r3.conclave.common.internal

import com.r3.conclave.common.OpaqueBytes
import com.r3.conclave.common.SHA256Hash
import com.r3.conclave.common.SHA512Hash
import com.r3.conclave.common.EnclaveInstanceInfo
import com.r3.conclave.common.EnclaveMode
import com.r3.conclave.common.SGXEnclaveSecurityInfo
import com.r3.conclave.common.internal.SgxAttributes.flags
import com.r3.conclave.common.internal.SgxQuote.reportBody
import com.r3.conclave.common.internal.SgxQuote.version
import com.r3.conclave.common.internal.SgxReportBody.attributes
import com.r3.conclave.common.internal.SgxReportBody.reportData
import com.r3.conclave.common.internal.attestation.AttestationReport
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
    private val signedQuote = Cursor.allocate(SgxSignedQuote(500)).apply {
        quote[reportBody].apply {
            this[reportData] = SHA512Hash.hash(signingKeyPair.public.encoded).buffer()
            this[SgxReportBody.cpuSvn] = cpuSvn.buffer()
            this[SgxReportBody.measurement] = measurement.buffer()
            this[SgxReportBody.mrsigner] = mrsigner.buffer()
            this[attributes][flags] = SgxEnclaveFlags.DEBUG
        }
    }

    private var reportQuoteStatus: QuoteStatus = QuoteStatus.OK
    private var reportTimestamp: Instant = Instant.now()
    private var advisoryIds: List<String> = emptyList()

    private val attestationService = object : MockAttestationService() {
        override fun modifyReport(report: AttestationReport): AttestationReport {
            return report.copy(isvEnclaveQuoteStatus = reportQuoteStatus, timestamp = reportTimestamp)
        }
        override fun advisoryIds() = advisoryIds
    }

    @Test
    fun `signed quote is different to the one in the attestation report`() {
        val differentSignedQuote = cloneSignedQuote()
        differentSignedQuote.quote[version] = 9
        val response = attestationService.requestSignature(signedQuote)
        assertThatIllegalArgumentException().isThrownBy {
            EnclaveInstanceInfoImpl(signingKeyPair.public, differentSignedQuote, response, EnclaveMode.SIMULATION)
        }.withMessage("The quote in the attestation report is not the one that was provided for attestation.")
    }

    @Test
    fun `report data is not the signing key hash`() {
        val differentSignedQuote = cloneSignedQuote()
        differentSignedQuote.quote[reportBody][reportData] = ByteBuffer.wrap(Random.nextBytes(64))
        val response = attestationService.requestSignature(differentSignedQuote)
        assertThatIllegalArgumentException().isThrownBy {
            EnclaveInstanceInfoImpl(signingKeyPair.public, differentSignedQuote, response, EnclaveMode.SIMULATION)
        }.withMessage("The report data of the quote does not equal the SHA-512 hash of the data signing key.")
    }

    @Test
    fun verifier() {
        val message = Random.nextBytes(100)
        val signature = SignatureSchemeEdDSA.createSignature().run {
            initSign(signingKeyPair.private)
            update(message)
            sign()
        }
        val verifier = create().verifier()
        verifier.update(message)
        assertThat(verifier.verify(signature)).isTrue()
    }

    @Test
    fun enclaveInfo() {
        val enclaveInfo = create().enclaveInfo
        assertThat(enclaveInfo.codeHash).isEqualTo(measurement)
        assertThat(enclaveInfo.codeSigningKeyHash).isEqualTo(mrsigner)
        assertThat(enclaveInfo.enclaveMode).isEqualTo(EnclaveMode.SIMULATION)
    }

    @Test
    fun securityInfo() {
        val securityInfo = create().securityInfo as SGXEnclaveSecurityInfo
        assertThat(securityInfo.timestamp).isEqualTo(reportTimestamp)
        assertThat(securityInfo.cpuSVN).isEqualTo(cpuSvn)
    }

    @Test
    fun `serialisation round-trip`() {
        reportQuoteStatus = QuoteStatus.GROUP_OUT_OF_DATE
        advisoryIds = listOf("Note-1", "Note-2")
        val original = create()
        val deserialised = EnclaveInstanceInfo.deserialize(original.serialize()) as EnclaveInstanceInfoImpl
        assertThat(deserialised.dataSigningKey).isEqualTo(original.dataSigningKey)
        assertThat(deserialised.signedQuote.read()).isEqualTo(original.signedQuote.read())
        assertThat(deserialised.attestationResponse.reportBytes).isEqualTo(original.attestationResponse.reportBytes)
        assertThat(deserialised.attestationResponse.signature).isEqualTo(original.attestationResponse.signature)
        assertThat(deserialised.attestationResponse.certPath).isEqualTo(original.attestationResponse.certPath)
        assertThat(deserialised.attestationResponse.advisoryIds).isEqualTo(original.attestationResponse.advisoryIds).isEqualTo(listOf("Note-1", "Note-2"))
        assertThat(deserialised.enclaveMode).isEqualTo(original.enclaveMode)
    }

    private fun cloneSignedQuote(): ByteCursor<SgxSignedQuote> {
        return Cursor(SgxSignedQuote(signedQuote.encoder.size()), signedQuote.readBytes())
    }

    private fun create(): EnclaveInstanceInfoImpl {
        val response = attestationService.requestSignature(signedQuote)
        return EnclaveInstanceInfoImpl(signingKeyPair.public, signedQuote, response, EnclaveMode.SIMULATION)
    }
}
