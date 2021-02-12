package com.r3.conclave.common.internal

import com.r3.conclave.common.OpaqueBytes
import com.r3.conclave.common.SHA256Hash
import com.r3.conclave.common.SHA512Hash
import com.r3.conclave.common.internal.attestation.MockAttestation
import com.r3.conclave.mail.Curve25519PrivateKey
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatIllegalArgumentException
import org.junit.jupiter.api.Test
import java.nio.ByteBuffer
import java.time.Instant
import kotlin.random.Random

/**
 * Tests on EnclaveInstanceInfoImpl where the attestation protocol doesn't matter.
 */
class EnclaveInstanceInfoImplTest {
    private val signingKeyPair = SignatureSchemeEdDSA().generateKeyPair()
    private val encryptionPrivateKey = Curve25519PrivateKey.random()
    private val measurement = SHA256Hash.wrap(Random.nextBytes(32))
    private val cpuSvn = OpaqueBytes(Random.nextBytes(16))
    private val mrsigner = SHA256Hash.wrap(Random.nextBytes(32))
    private val isvProdId = 65535  // Max allowed product ID
    private val isvSvn = 65535   // Max allowed ISVSVN
    private val reportBody = Cursor.allocate(SgxReportBody).apply {
        this[SgxReportBody.cpuSvn] = cpuSvn.buffer()
        this[SgxReportBody.mrenclave] = measurement.buffer()
        this[SgxReportBody.mrsigner] = mrsigner.buffer()
        this[SgxReportBody.isvProdId] = isvProdId
        this[SgxReportBody.isvSvn] = isvSvn
        this[SgxReportBody.reportData] =
            SHA512Hash.hash(signingKeyPair.public.encoded + encryptionPrivateKey.publicKey.encoded).buffer()
    }

    private val timestamp = Instant.now()

    @Test
    fun `report data is not the signing key hash`() {
        reportBody[SgxReportBody.reportData] = ByteBuffer.wrap(Random.nextBytes(64))
        assertThatIllegalArgumentException().isThrownBy {
            newInstance()
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
        assertThat(securityInfo.timestamp).isEqualTo(timestamp)
        assertThat(securityInfo.cpuSVN).isEqualTo(cpuSvn)
    }

    private fun newInstance(): EnclaveInstanceInfoImpl {
        return EnclaveInstanceInfoImpl(
            signingKeyPair.public,
            encryptionPrivateKey.publicKey,
            MockAttestation(timestamp, reportBody.asReadOnly(), false)
        )
    }
}
