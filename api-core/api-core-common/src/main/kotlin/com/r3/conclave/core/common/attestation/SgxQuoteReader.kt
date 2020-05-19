package com.r3.conclave.core.common.attestation

import com.r3.conclave.common.internal.ByteCursor
import com.r3.conclave.common.internal.SgxAttributes
import com.r3.conclave.common.internal.SgxQuote
import com.r3.conclave.common.internal.SgxReportBody
import java.nio.ByteBuffer

/**
 * Helper class providing access to a subset of the fields in a quote structure.
 */
class SgxQuoteReader(private val quoteCursor: ByteCursor<SgxQuote>) {

    /**
     * The enclave measurement reported in the quote
     */
    val measurement: ByteBuffer
        get() = quoteCursor[SgxQuote.reportBody][SgxReportBody.measurement].read().asReadOnlyBuffer()

    /**
     * The enclave generated payload, a.k.a the `reportData` field in the SGX framework
     */
    val reportData: ByteBuffer
        get() = quoteCursor[SgxQuote.reportBody][SgxReportBody.reportData].read().asReadOnlyBuffer()

    /**
     * A cursor object to access the attributes mask
     */
    val attributesCursor: ByteCursor<SgxAttributes>
        get() = quoteCursor[SgxQuote.reportBody][SgxReportBody.attributes]
}