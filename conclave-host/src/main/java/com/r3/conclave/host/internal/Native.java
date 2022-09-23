package com.r3.conclave.host.internal;

/**
 * The JNI interface of the host. Requires symbols loaded with [NativeLoader]
 */
public class Native {
    public static native long createEnclave(String path, boolean isDebug);

    public static native void destroyEnclave(long enclaveId);

    // TODO use ByteBuffers directly
    public static native void jvmEcall(long enclaveId, byte[] data);

    // TODO: Temporary for con 1025
    public static native void jvmEcallCon1025(long enclaveId, short callType, byte messageTypeID, byte[] data);

    /**
     * sgx_status_t sgx_init_quote(sgx_target_info_t *p_target_info, sgx_epid_group_id_t *p_gid)
     */
    public static native void initQuote(byte[] initQuoteResponseOut);

    /**
     * sgx_status_t SGXAPI sgx_calc_quote_size(
     *     const uint8_t *p_sig_rl,
     *     uint32_t sig_rl_size,
     *     uint32_t* p_quote_size
     * );
     */
    public static native int calcQuoteSize(byte[] signatureRevocationListIn);

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
    public static native void getQuote(
        byte[] getQuoteRequestIn,
        byte[] signatureRevocationListIn,
        byte[] quotingEnclaveReportNonceIn,
        byte[] quotingEnclaveReportOut,
        byte[] quoteOut
    );


    /*
        // these are only used internally for quote verification
        enum class PckCaType {
            Processor,
            Platform
        }

        enum class CollateralType {
            Version,
            PckCrlIssuerChain,
            RootCaCrl,
            PckCrl,
            TcbInfoIssuerChain,
            TcbInfo,
            QeIdentityIssuerChain,
            QeIdentity
        }
    */
    public static native int initQuoteDCAP(String bundlePath, byte[] initQuoteResponseOut); // 0 --> OK
    public static native int calcQuoteSizeDCAP();  // > 0 --> OK
    public static native int getQuoteDCAP(byte[] quoteRequestIn, byte[] quoteOut); // 0 --> OK
    public static native Object[] getQuoteCollateral(byte[] fmspc, int pck);

    // SgxMetadata
    public static native void getMetadata(
        String enclavePath,
        byte[] metadataOut
    );

    public static native String getCpuCapabilitiesSummary();
}
