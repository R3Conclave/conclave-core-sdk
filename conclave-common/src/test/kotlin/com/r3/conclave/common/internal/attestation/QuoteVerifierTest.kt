package com.r3.conclave.common.internal.attestation

import com.fasterxml.jackson.databind.ObjectMapper
import com.r3.conclave.common.EnclaveInstanceInfo
import com.r3.conclave.common.OpaqueBytes
import com.r3.conclave.common.internal.Cursor
import com.r3.conclave.common.internal.EnclaveInstanceInfoImpl
import com.r3.conclave.common.internal.SgxQuote.signType
import com.r3.conclave.common.internal.SgxQuote.version
import com.r3.conclave.common.internal.SgxSignedQuote
import com.r3.conclave.common.internal.SgxSignedQuote.quote
import com.r3.conclave.utilities.internal.readFully
import com.r3.conclave.utilities.internal.toHexString
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.nio.ByteBuffer
import kotlin.random.Random

class QuoteVerifierTest {
    companion object {
        fun loadSampleDcapAttestation(iceLake: Boolean = false): DcapAttestation {
            val input = QuoteVerifierTest::class.java.getResourceAsStream(
                if (iceLake) "EnclaveInstanceInfo-DCAP-IceLake.ser" else "EnclaveInstanceInfo-DCAP.ser"
            )!!.readFully()
            return (EnclaveInstanceInfo.deserialize(input) as EnclaveInstanceInfoImpl).attestation as DcapAttestation
        }

        /**
         * Find the index of a subsequence within a byte array.
         * Returns -1 if the sequence isn't found.
         */
        fun ByteArray.findSubsequence(subsequence: ByteArray): Int {
            check(subsequence.isNotEmpty())

            val lowestPossibleIndex = this.size - subsequence.size

            if (lowestPossibleIndex < 0) {
                return -1
            }

            for (i in 0 .. lowestPossibleIndex) {
                var found = true
                for (j in subsequence.indices) {
                    if (this[i + j] != subsequence[j]) {
                        found = false
                        break
                    }
                }
                if (found) {
                    return i
                }
            }

            return -1
        }
    }

    @Test
    fun `subsequence finder test`() {
        val array = byteArrayOf(0, 1, 2, 3, 4, 5, 6, 7, 8, 9)
        assertThat(array.findSubsequence(byteArrayOf(0))).isEqualTo(0)
        assertThat(array.findSubsequence(byteArrayOf(9))).isEqualTo(9)
        assertThat(array.findSubsequence(byteArrayOf(0, 1, 2, 3, 4, 5, 6, 7, 8, 9))).isEqualTo(0)
        assertThat(array.findSubsequence(byteArrayOf(7, 8, 9))).isEqualTo(7)
        assertThat(array.findSubsequence(byteArrayOf(3, 4, 5))).isEqualTo(3)
        assertThat(array.findSubsequence(byteArrayOf(0, 9))).isEqualTo(-1)
        assertThat(array.findSubsequence(byteArrayOf(10))).isEqualTo(-1)
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
        val badPckCrlCollateral = collateral.copy(
            rawPckCrl = OpaqueBytes(javaClass.getResourceAsStream("sample.root.crl.pem")!!.readFully())
        )

        val (status) = QuoteVerifier.verify(
            signedQuote,
            badPckCrlCollateral
        )

        assertThat(status).isEqualTo(QuoteVerifier.ErrorStatus.SGX_CRL_UNKNOWN_ISSUER)
    }

    @Test
    fun `tcbinfo bad signature`() {
        val (signedQuote, collateral) = loadSampleDcapAttestation()
        val rawSignedTcbInfo = collateral.rawSignedTcbInfo.bytes
        val badSignature = Random.nextBytes(64)

        // We don't want to have to maintain serializing code for the TcbInfo, so we apply a hack
        // directly to the serialized json, overwriting only the field we're interested in.
        val signaturePattern = "\"signature\":\"".toByteArray()
        val index = rawSignedTcbInfo.findSubsequence(signaturePattern)
        check(index != -1) { "Failed to find signature bytes!" }

        // Overwrite the good value with the bad one
        val badRawSignedTcbInfo = ByteBuffer.wrap(rawSignedTcbInfo).apply {
            position(index + signaturePattern.size)
            put(badSignature.toHexString().toByteArray())
        }.array()

        // Check that the bad signature was installed correctly
        val json = ObjectMapper().readTree(rawSignedTcbInfo.inputStream())
        assertThat(SignedTcbInfo.fromJson(json).signature.bytes).isEqualTo(badSignature)

        val badTcbInfoCollateral = collateral.copy(rawSignedTcbInfo = OpaqueBytes(badRawSignedTcbInfo))

        val (status) = QuoteVerifier.verify(
            signedQuote,
            badTcbInfoCollateral
        )

        assertThat(status).isEqualTo(QuoteVerifier.ErrorStatus.TCB_INFO_INVALID_SIGNATURE)
    }

    @Test
    fun `tcbinfo bad data`() {
        val (signedQuote, collateral) = loadSampleDcapAttestation()
        val rawSignedTcbInfo = collateral.rawSignedTcbInfo.bytes
        val badPceId = Random.nextBytes(2)

        // We don't want to have to maintain serializing code for the TcbInfo, so we apply a hack
        // directly to the serialized json, overwriting only the field we're interested in.
        val signaturePattern = "\"pceId\":\"".toByteArray()
        val index = rawSignedTcbInfo.findSubsequence(signaturePattern)
        check(index != -1) { "Failed to find pceId bytes!" }

        // Overwrite the good value with the bad one
        val badRawSignedTcbInfo = ByteBuffer.wrap(rawSignedTcbInfo).apply {
            position(index + signaturePattern.size)
            put(badPceId.toHexString().toByteArray())
        }.array()

        // Check that the bad value was installed correctly
        val json = ObjectMapper().readTree(rawSignedTcbInfo.inputStream())
        assertThat(SignedTcbInfo.fromJson(json).tcbInfo.pceId.bytes).isEqualTo(badPceId)

        val badTcbInfoCollateral = collateral.copy(rawSignedTcbInfo = OpaqueBytes(badRawSignedTcbInfo))

        val (status) = QuoteVerifier.verify(
            signedQuote,
            badTcbInfoCollateral
        )

        assertThat(status).isEqualTo(QuoteVerifier.ErrorStatus.TCB_INFO_INVALID_SIGNATURE)
    }
}
