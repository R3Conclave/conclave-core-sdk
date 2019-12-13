package com.r3.sgx.enclavelethost.client

import com.r3.sgx.core.common.Cursor
import com.r3.sgx.core.common.SgxMeasurement
import com.r3.sgx.core.common.SgxQuote
import com.r3.sgx.core.common.SgxReportBody
import com.r3.sgx.core.common.attestation.Measurement
import java.nio.ByteBuffer
import java.security.GeneralSecurityException

/**
 * Define a verification constraint that an Sgx Quote need to satisfy
 * in order to be trusted by the client
 */
sealed class QuoteConstraint {

    /**
     * Verify input quote satisfies this constraints, throwing an exception if it doesnt
     */
    abstract fun verify(quote: Cursor<ByteBuffer, SgxQuote>)

    /**
     * A QuoteConstraint enforcing the enclave measurement is part of a given trusted set
     */
    data class ValidMeasurements(val trustedMeasurements: Set<Measurement>): QuoteConstraint() {
        constructor(vararg measurements: Measurement): this(setOf(*measurements))
        override fun verify(quote: Cursor<ByteBuffer, SgxQuote>) {
            val measurementBytes = ByteArray(SgxMeasurement.size).also {
                quote[SgxQuote.reportBody][SgxReportBody.measurement].read().get(it)
            }
            val measurement = Measurement(measurementBytes)
            if (measurement !in trustedMeasurements) {
                throw GeneralSecurityException("Measurement $measurement not in trusted set")
            }
        }
        override fun toString() = "QuoteConstraint.ValidMeasurements({trustedMeasurements.joinToString()})"
    }
}