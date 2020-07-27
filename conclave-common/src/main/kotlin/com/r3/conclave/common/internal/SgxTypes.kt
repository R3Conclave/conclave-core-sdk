package com.r3.conclave.common.internal

// This file defines typed views reflecting the SGX API.

enum class SgxQuoteType(val value: Int) {
    UNLINKABLE(0),
    LINKABLE(1)
}

/**
 * 256-bit value representing the enclave measurement.
 *
 * @see `sgx_measurement_t`
 */
object SgxMeasurement : FixedBytes(32)

/**
 * 128-bit value representing the CPU security version. Use this value in key derivation and obtain it by getting an
 * enclave report (`sgx_create_report`).
 *
 * @see `sgx_cpu_svn_t`
 */
object SgxCpuSvn : FixedBytes(16)

/**
 * 512-bit value used for communication between the enclave and the target enclave. This is one of the inputs to the
 * `sgx_create_report` function.
 *
 * @see `sgx_report_data_t`
 */
object SgxReportData : FixedBytes(64)

/**
 * 256-bit value used in the key request structure. The value is generally populated with a random value to provide key
 * wear-out protection.
 *
 * @see `sgx_key_id_t`
 */
object SgxKeyId : FixedBytes(32)

/**
 * This type is utilized as storage for the 128-bit CMAC value of the report data.
 *
 * @see `sgx_mac_t`
 */
object SgxMac : FixedBytes(16)

/**
 * Type for Intel® EPID group id.
 *
 * @see `sgx_epid_group_id_t`
 */
object SgxEpidGroupId : FixedBytes(4)

/**
 * Type for a service provider ID.
 *
 * @see `sgx_spid_t`
 */
object SgxSpid : FixedBytes(16)

/**
 * This data structure indicates the quote nonce.
 *
 * @see `sgx_quote_nonce_t`
 */
object SgxQuoteNonce : FixedBytes(16)

/**
 * Type for base name used in `sgx_quote`.
 *
 * @see `sgx_basename_t`
 */
object SgxBasename : FixedBytes(32)

/**
 * A 16-bit value representing the ISV enclave product ID. This value is used in the derivation of some keys.
 *
 * @see `sgx_prod_id_t`
 */
object SgxProdId : UInt16()

/**
 * ISV security version. The value is 2 bytes in length. Use this value in key derivation and obtain it by getting an
 * enclave report (`sgx_create_report`).
 *
 * @see `sgx_isv_svn_t`
 */
object SgxIsvSvn : UInt16()

/**
 * 16-bit value representing the enclave CONFIGSVN. This value is used in the derivation of some keys.
 *
 * @see `sgx_config_svn_t`
 */
object SgxConfigSvn : UInt16()

/**
 * Enclave misc select bits. The value is 4 byte in length. Currently all the bits are reserved for future extension.
 *
 * @see `sgx_misc_select_t`
 */
object SgxMiscSelect : UInt32()

/**
 * 64-byte value representing the enclave CONFIGID. This value is used in the derivation of some keys.
 *
 * @see `sgx_config_id_t`
 */
object SgxConfigId : FixedBytes(64)

/**
 * 16-byte value representing the enclave Extended Product ID. This value is used in the derivation of some keys.
 *
 * @see `sgx_isvext_prod_id_t`
 */
object SgxIsvExtProdId : FixedBytes(16)

/**
 * 16-byte value representing the enclave product Family ID. This value is used in the derivation of some keys.
 *
 * @see `sgx_isvfamily_id_t`
 */
object SgxIsvFamilyId : FixedBytes(16)

// The sdk has two versions
object SgxQuoteType32 : Enum32() {
    @JvmField val UNLINKABLE: Int = SgxQuoteType.UNLINKABLE.value
    @JvmField val LINKABLE: Int = SgxQuoteType.LINKABLE.value
}

object SgxQuoteType16 : Enum16() {
    @JvmField val UNLINKABLE: Int = SgxQuoteType.UNLINKABLE.value
    @JvmField val LINKABLE: Int = SgxQuoteType.LINKABLE.value
}

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
object SgxGetQuote : Struct() {
    @JvmField val report = field(SgxReport)
    @JvmField val quoteType = field(SgxQuoteType32)
    @JvmField val spid = field(SgxSpid)
}

/**
 * sgx_status_t sgx_init_quote(sgx_target_info_t *p_target_info, sgx_epid_group_id_t *p_gid)
 */
object SgxInitQuoteResponse : Struct() {
    @JvmField val quotingEnclaveTargetInfo = field(SgxTargetInfo)
    @JvmField val epidGroupId = field(SgxEpidGroupId)
}

/**
 * Type for quote used in remote attestation.
 *
 * @see `sgx_quote_t`
 */
object SgxQuote : Struct() {
    /** The version of the quote structure. */
    @JvmField val version = field(UInt16())
    /** The indicator of the Intel® EPID signature type. */
    @JvmField val signType = field(SgxQuoteType16)
    /** The Intel® EPID group id of the platform belongs to. */
    @JvmField val epidGroupId = field(SgxEpidGroupId)
    /** The svn of the QE. */
    @JvmField val qeIsvSvn = field(SgxIsvSvn)
    /** The svn of the PCE. */
    @JvmField val pceIsvSvn = field(SgxIsvSvn)
    @JvmField val extendedEpidGroupId = field(UInt32())
    /** The base name used in sgx_quote. */
    @JvmField val basename = field(SgxBasename)
    /** The report body of the application enclave. */
    @JvmField val reportBody = field(SgxReportBody)
}

/**
 * Type for quote used in remote attestation.
 *
 * @see `sgx_quote_t`
 */
class SgxSignedQuote(quoteSize: Int) : Struct() {
    /** This has been split out into its own because it some circumstances the signature isn't used. */
    @JvmField val quote = field(SgxQuote)
    /** The size in byte of the following signature. */
    @Suppress("unused")
    @JvmField val signatureSize = field(UInt32())
    /** The place holder of the variable length signature. */
    @JvmField val signature = field(FixedBytes(quoteSize - size()))
}

val ByteCursor<SgxSignedQuote>.quote: ByteCursor<SgxQuote> get() = this[encoder.quote]

/**
 * Enclave attributes definition structure.
 *
 * Note: When specifying an attributes mask used in key derivation, at a minimum the flags that should be set are
 * [SgxEnclaveFlags.INITTED], [SgxEnclaveFlags.DEBUG] and RESERVED bits.
 *
 * @see `sgx_attributes_t`
 */
object SgxAttributes : Struct() {
    @JvmField val flags = field(SgxEnclaveFlags)
    @JvmField val xfrm = field(SgxXfrmFlags)
}

/**
 * Data structure of report target information. This is an input to functions `sgx_create_report` and `sgx_init_quote`,
 * which are used to identify the enclave (its measurement and attributes), which will be able to verify the generated
 * REPORT.
 *
 * @see `sgx_target_info_t`
 */
object SgxTargetInfo : Struct() {
    /** Enclave hash of the target enclave. */
    @JvmField val measurement = field(SgxMeasurement)
    /** Attributes of the target enclave. */
    @JvmField val attributes = field(SgxAttributes)
    /** Reserved for future use. Must be set to zero. */
    @Suppress("unused")
    @JvmField val reserved1 = field(ReservedBytes(2))
    /** Enclave CONFIGSVN. */
    @JvmField val configSvn = field(SgxConfigSvn)
    /** Misc select bits for the target enclave. Reserved for future function extension. */
    @JvmField val miscSelect = field(SgxMiscSelect)
    /** Reserved for future use. Must be set to zero. */
    @Suppress("unused")
    @JvmField val reserved2 = field(ReservedBytes(8))
    /** Enclave CONFIGID. */
    @JvmField val configId = field(SgxConfigId)
    /** Reserved for future use. Must be set to zero. */
    @Suppress("unused")
    @JvmField val reserved3 = field(ReservedBytes(384))
}

/**
 * Data structure that contains information about the enclave. This data structure is a part of the `sgx_report_t`
 * structure.
 *
 * @see `sgx_report_body_t`
 */
object SgxReportBody : Struct() {
    /** Security version number of the host system TCB (CPU). */
    @JvmField val cpuSvn = field(SgxCpuSvn)
    /** Misc select bits for the target enclave. Reserved for future function extension. */
    @JvmField val miscSelect = field(SgxMiscSelect)
    /** Reserved for future use. Must be set to zero. */
    @Suppress("unused")
    @JvmField val reserved1 = field(ReservedBytes(12))
    /** ISV assigned Extended Product ID. */
    @JvmField val isvExtProdId = field(SgxIsvExtProdId)
    /** Attributes for the enclave. */
    @JvmField val attributes = field(SgxAttributes)
    /** Measurement value of the enclave. */
    @JvmField val mrenclave = field(SgxMeasurement)
    /** Reserved for future use. Must be set to zero. */
    @Suppress("unused")
    @JvmField val reserved2 = field(ReservedBytes(32))
    /** Measurement value of the public key that verified the enclave. */
    @JvmField val mrsigner = field(SgxMeasurement)
    /** Reserved for future use. Must be set to zero. */
    @Suppress("unused")
    @JvmField val reserved3 = field(ReservedBytes(32))
    /** The enclave CONFIGID. */
    @JvmField val configId = field(SgxConfigId)
    /** ISV Product ID of the enclave. */
    @JvmField val isvProdId = field(SgxProdId)
    /** ISV security version number of the enclave. */
    @JvmField val isvSvn = field(SgxIsvSvn)
    /** CONFIGSVN field. */
    @JvmField val configSvn = field(SgxConfigSvn)
    /** Reserved for future use. Must be set to zero. */
    @Suppress("unused")
    @JvmField val reserved4 = field(ReservedBytes(42))
    /** ISV assigned Family ID. */
    @JvmField val isvFamilyId = field(SgxIsvFamilyId)
    /** Set of data used for communication between the enclave and the target enclave.*/
    @JvmField val reportData = field(SgxReportData)
}

/**
 * Data structure that contains the report information for the enclave. This is the output parameter from the
 * `sgx_create_report` function. This is the input parameter for the `sgx_init_quote` function.
 *
 * @see `sgx_report_t`
 */
object SgxReport : Struct() {
    /** The data structure containing information about the enclave. */
    @JvmField val body = field(SgxReportBody)
    /** Value for key wear-out protection. */
    @JvmField val keyId = field(SgxKeyId)
    /** The CMAC value of the report data using report key. */
    @JvmField val mac = field(SgxMac)
}

/**
 * @see SgxAttributes.flags
 */
object SgxEnclaveFlags : Flags64() {
    /** The enclave is initialized. */
    const val INITTED        = 0x0000000000000001L
    /** The enclave is a debug enclave. */
    const val DEBUG          = 0x0000000000000002L
    /** The enclave runs in 64 bit mode. */
    const val MODE64BIT      = 0x0000000000000004L
    /** The enclave has access to a provision key. */
    const val PROVISION_KEY  = 0x0000000000000010L
    /** The enclave has access to a launch key */
    const val EINITTOKEN_KEY = 0x0000000000000020L
    /** The enclave requires the KSS feature. */
    const val KSS            = 0x0000000000000080L
}

/**
 * @see SgxAttributes.xfrm
 */
object SgxXfrmFlags : Flags64() {
    /** FPU and Intel® Streaming SIMD Extensions states are saved. */
    const val LEGACY = 0x0000000000000003L
    /** Intel® Advanced Vector Extensions state is saved. */
    const val AVX    = 0x0000000000000006L
}
