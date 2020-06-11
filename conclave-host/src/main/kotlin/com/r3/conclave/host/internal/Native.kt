package com.r3.conclave.host.internal

/**
 * The JNI interface of the host. Requires symbols loaded with [NativeLoader]
 */
object Native {
    @Throws(Exception::class)
    external fun createEnclave(path: String, isDebug: Boolean): Long

    @Throws(Exception::class)
    external fun destroyEnclave(enclaveId: Long)

    // TODO use ByteBuffers directly
    @Throws(Exception::class)
    external fun jvmEcall(enclaveId: Long, data: ByteArray)

    /**
     * sgx_status_t sgx_init_quote(sgx_target_info_t *p_target_info, sgx_epid_group_id_t *p_gid)
     */
    external fun initQuote(initQuoteResponseOut: ByteArray)

    /**
     * sgx_status_t SGXAPI sgx_calc_quote_size(
     *     const uint8_t *p_sig_rl,
     *     uint32_t sig_rl_size,
     *     uint32_t* p_quote_size
     * );
    */
    external fun calcQuoteSize(signatureRevocationListIn: ByteArray?): Int

    /**
     * sgx_status_t SGXAPI sgx_get_quote(
     *     const sgx_report_t *p_report,
     *     sgx_quote_sign_type_t quote_type,
     *     const sgx_spid_t *p_spid,
     *     const sgx_quote_nonce_t *p_nonce,
     *     const uint8_t *p_sig_rl,
     *     uint32_t sig_rl_size,
     *     sgx_report_t *p_qe_report,
     *     sgx_quote_t *p_quote,
     *     uint32_t quote_size
     * );
     */
    external fun getQuote(
            getQuoteRequestIn: ByteArray,
            signatureRevocationListIn: ByteArray?,
            quotingEnclaveReportNonceIn: ByteArray?,
            quotingEnclaveReportOut: ByteArray?,
            quoteOut: ByteArray
    )

    // SgxMetadata
    external fun getMetadata(
            enclavePath: String,
            metadataOut: ByteArray
    )

}
