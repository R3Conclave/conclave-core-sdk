package com.r3.conclave.common.internal

import com.r3.conclave.common.*
import com.r3.conclave.common.EnclaveMode.MOCK
import com.r3.conclave.common.EnclaveMode.SIMULATION
import com.r3.conclave.common.internal.SgxQuote.reportBody
import com.r3.conclave.common.internal.SgxReportBody.reportData
import com.r3.conclave.common.internal.attestation.AttestationReport
import com.r3.conclave.common.internal.attestation.QuoteStatus
import com.r3.conclave.host.internal.MockAttestationService
import com.r3.conclave.internaltesting.createSignedQuote
import com.r3.conclave.mail.Curve25519KeyPairGenerator
import com.r3.conclave.mail.Curve25519PublicKey
import org.assertj.core.api.Assertions.*
import org.junit.jupiter.api.Test
import java.nio.ByteBuffer
import java.time.Instant
import kotlin.random.Random

class EnclaveInstanceInfoImplTest {
    private val signingKeyPair = SignatureSchemeEdDSA().generateKeyPair()
    private val encryptionKeyPair = Curve25519KeyPairGenerator().generateKeyPair()
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
            dataSigningKey = signingKeyPair.public,
            encryptionKey = encryptionKeyPair.public
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
        }.withMessage("The report data of the quote does not match the hash in the remote attestation.")
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
    }

    @Test
    fun securityInfo() {
        val securityInfo = newInstance().securityInfo
        assertThat(securityInfo.timestamp).isEqualTo(reportTimestamp)
        assertThat(securityInfo.cpuSVN).isEqualTo(cpuSvn)
    }

    @Test
    fun `simulation and mock modes are always insecure`() {
        reportQuoteStatus = QuoteStatus.OK
        newInstance(enclaveMode = SIMULATION).let {
            assertThat(it.enclaveMode).isEqualTo(SIMULATION)
            assertThat(it.securityInfo.summary).isEqualTo(EnclaveSecurityInfo.Summary.INSECURE)
        }
        newInstance(enclaveMode = MOCK).let {
            assertThat(it.enclaveMode).isEqualTo(MOCK)
            assertThat(it.securityInfo.summary).isEqualTo(EnclaveSecurityInfo.Summary.INSECURE)
        }
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

    @Test
    fun `EnclaveInstanceInfo deserialize throws IllegalArgumentException on truncated bytes`() {
        val serialised = newInstance().serialize()
        for (truncatedSize in serialised.indices) {
            val truncated = serialised.copyOf(truncatedSize)
            assertThatIllegalArgumentException().describedAs("Truncated size $truncatedSize").isThrownBy {
                EnclaveInstanceInfo.deserialize(truncated)
            }
        }
    }

    private fun ByteCursor<SgxSignedQuote>.copy(modify: ByteCursor<SgxSignedQuote>.() -> Unit): ByteCursor<SgxSignedQuote> {
        val copy = Cursor.wrap(SgxSignedQuote(encoder.size), bytes)
        modify(copy)
        return copy
    }

    private fun newInstance(
            signedQuote: ByteCursor<SgxSignedQuote> = this.signedQuote,
            enclaveMode: EnclaveMode = MOCK
    ): EnclaveInstanceInfoImpl {
        return attestationService.doAttest(signingKeyPair.public, encryptionKeyPair.public as Curve25519PublicKey, signedQuote, enclaveMode)
    }
}
