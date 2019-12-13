package com.r3.sgx.core.common.attestation

import com.r3.sgx.core.common.*
import java.nio.ByteBuffer

/**
 * Helper class providing access to a subset of the fields in a quote structure.
 */
class SgxQuoteReader(private val quoteCursor: Cursor<ByteBuffer, SgxQuote>) {

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
    val attributesCursor: Cursor<ByteBuffer, SgxAttributes>
        get() = quoteCursor[SgxQuote.reportBody][SgxReportBody.attributes]
}