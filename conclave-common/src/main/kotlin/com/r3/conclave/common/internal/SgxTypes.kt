package com.r3.conclave.common.internal

// This file defines typed views reflecting the SGX API.

enum class SgxQuoteType(val value: Int) {
    UNLINKABLE(0),
    LINKABLE(1)
}

object SgxMeasurement : FixedBytes(32)
object SgxMrsigner : FixedBytes(32)
object SgxCpuSvn : FixedBytes(16)
object SgxReportData : FixedBytes(64)
object SgxKeyId : FixedBytes(32)
object SgxMac : FixedBytes(16)
object SgxEpidGroupId : FixedBytes(4)
object SgxSpid : FixedBytes(16)
object SgxQuoteNonce : FixedBytes(16)
object SgxBasename : FixedBytes(32)

object SgxIsvProdId : Int16()
object SgxIsvSvn : Int16()
object SgxMiscSelect : Int32()

object Constants {
    const val SE_KEY_SIZE = 384
    const val SE_EXPONENT_SIZE = 4
    const val SGX_TARGET_INFO_RESERVED1_BYTES = 4
    const val SGX_TARGET_INFO_RESERVED2_BYTES = 456
}

// The sdk has two versions
object SgxQuoteType32 : Enum32() {
    @JvmField val UNLINKABLE: Int = SgxQuoteType.UNLINKABLE.value
    @JvmField val LINKABLE: Int = SgxQuoteType.LINKABLE.value
}

object SgxQuoteType16 : Enum16() {
    @JvmField val UNLINKABLE: Short = SgxQuoteType.UNLINKABLE.value.toShort()
    @JvmField val LINKABLE: Short = SgxQuoteType.LINKABLE.value.toShort()
}

object DirIndex : Enum32() {
    @JvmField val DIR_PATCH: Int = 0
    @JvmField val DIR_LAYOUT: Int = 1
    @JvmField val DIR_NUM: Int = 2
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
typedef struct _quote_t
{
    uint16_t            version;        /* 0   */
    uint16_t            sign_type;      /* 2   */
    sgx_epid_group_id_t epid_group_id;  /* 4   */
    sgx_isv_svn_t       qe_svn;         /* 8   */
    sgx_isv_svn_t       pce_svn;        /* 10  */
    uint32_t            xeid;           /* 12  */
    sgx_basename_t      basename;       /* 16  */
    sgx_report_body_t   report_body;    /* 48  */
    uint32_t            signature_len;  /* 432 */
    uint8_t             signature[];    /* 436 */
} sgx_quote_t;

 We split the above into two, as in some circumstances the signature isn't used.
*/
object SgxQuote : Struct() {
    @JvmField val version = field(Int16())
    @JvmField val quoteType = field(SgxQuoteType16) // the api is inconsistent
    @JvmField val epidGroupId = field(SgxEpidGroupId)
    @JvmField val qeIsvSvn = field(SgxIsvSvn)
    @JvmField val pceIsvSvn = field(SgxIsvSvn)
    @JvmField val externalEpidGroupId = field(Int32())
    @JvmField val basename = field(SgxBasename)
    @JvmField val reportBody = field(SgxReportBody)
}
class SgxSignedQuote(quoteSize: Int) : Struct() {
    @JvmField val quote = field(SgxQuote)
    @JvmField val signatureSize = field(Int32())
    @JvmField val signature = field(FixedBytes(quoteSize - size()))
}

val ByteCursor<SgxSignedQuote>.quote: ByteCursor<SgxQuote> get() = this[encoder.quote]

/**
typedef struct _attributes_t
{
    uint64_t      flags;
    uint64_t      xfrm;
} sgx_attributes_t;
 */
object SgxAttributes : Struct() {
    @JvmField val flags = field(SgxEnclaveFlags)
    @JvmField val xfrm = field(SgxXfrmFlags)
}

/**
typedef struct _target_info_t
{
    sgx_measurement_t       mr_enclave;     /* (  0) The MRENCLAVE of the target enclave */
    sgx_attributes_t        attributes;     /* ( 32) The ATTRIBUTES field of the target enclave */
    uint8_t                 reserved1[SGX_TARGET_INFO_RESERVED1_BYTES];   /* ( 48) Reserved */
    sgx_misc_select_t       misc_select;    /* ( 52) The MISCSELECT of the target enclave */
    uint8_t                 reserved2[SGX_TARGET_INFO_RESERVED2_BYTES]; /* ( 56) Struct size is 512 bytes */
} sgx_target_info_t;
 */
object SgxTargetInfo : Struct() {
    @JvmField val measurement = field(SgxMeasurement)
    @JvmField val attributes = field(SgxAttributes)
    @JvmField val reserved1 = field(ReservedBytes(Constants.SGX_TARGET_INFO_RESERVED1_BYTES))
    @JvmField val miscSelect = field(SgxMiscSelect)
    @JvmField val reserved2 = field(ReservedBytes(Constants.SGX_TARGET_INFO_RESERVED2_BYTES))
}

/**
typedef struct _report_body_t
{
    sgx_cpu_svn_t           cpu_svn;        /* (  0) Security Version of the CPU */
    sgx_misc_select_t       misc_select;    /* ( 16) Which fields defined in SSA.MISC */
    uint8_t                 reserved1[28];  /* ( 20) */
    sgx_attributes_t        attributes;     /* ( 48) Any special Capabilities the Enclave possess */
    sgx_measurement_t       mr_enclave;     /* ( 64) The value of the enclave's ENCLAVE measurement */
    uint8_t                 reserved2[32];  /* ( 96) */
    sgx_measurement_t       mr_signer;      /* (128) The value of the enclave's SIGNER measurement */
    uint8_t                 reserved3[96];  /* (160) */
    sgx_prod_id_t           isv_prod_id;    /* (256) Product ID of the Enclave */
    sgx_isv_svn_t           isv_svn;        /* (258) Security Version of the Enclave */
    uint8_t                 reserved4[60];  /* (260) */
    sgx_report_data_t       report_data;    /* (320) Data provided by the user */
} sgx_report_body_t;
 */
object SgxReportBody : Struct() {
    @JvmField val cpuSvn = field(SgxCpuSvn)
    @JvmField val miscSelect = field(SgxMiscSelect)
    @JvmField val reserved1 = field(ReservedBytes(28))
    @JvmField val attributes = field(SgxAttributes)
    @JvmField val measurement = field(SgxMeasurement)
    @JvmField val reserved2 = field(ReservedBytes(32))
    @JvmField val mrsigner = field(SgxMrsigner)
    @JvmField val reserved3 = field(ReservedBytes(96))
    @JvmField val isvProdId = field(SgxIsvProdId)
    @JvmField val isvSvn = field(SgxIsvSvn)
    @JvmField val reserved4 = field(ReservedBytes(60))
    @JvmField val reportData = field(SgxReportData)
}

/**
typedef struct _report_t                    /* 432 bytes */
{
    sgx_report_body_t       body;
    sgx_key_id_t            key_id;         /* (384) KeyID used for diversifying the key tree */
    sgx_mac_t               mac;            /* (416) The Message Authentication Code over this structure. */
} sgx_report_t;
 */
object SgxReport : Struct() {
    @JvmField val body = field(SgxReportBody)
    @JvmField val keyId = field(SgxKeyId)
    @JvmField val mac = field(SgxMac)
}

/**
typedef struct _metadata_t
{
    uint64_t            magic_num;             /* The magic number identifying the file as a signed enclave image */
    uint64_t            version;               /* The metadata version */
    uint32_t            size;                  /* The size of this structure */
    uint32_t            tcs_policy;            /* TCS management policy */
    uint32_t            ssa_frame_size;        /* The size of SSA frame in page */
    uint32_t            max_save_buffer_size;  /* Max buffer size is 2632 */
    uint32_t            desired_misc_select;
    uint32_t            tcs_min_pool;          /* TCS min pool*/
    uint64_t            enclave_size;          /* enclave virtual size */
    sgx_attributes_t    attributes;            /* XFeatureMask to be set in SECS. */
    enclave_css_t       enclave_css;           /* The enclave signature */
    data_directory_t    dirs[DIR_NUM];
    uint8_t             data[18592];
} metadata_t;
 */
object SgxMetadata : Struct() {
    @JvmField val magicNum = field(FixedBytes(8))
    @JvmField val version = field(FixedBytes(8))
    @JvmField val size = field(Int32())
    @JvmField val tcsPolicy = field(Int32())
    @JvmField val ssaFrameSize = field(Int32())
    @JvmField val maxSaveBufferSize = field(Int32())
    @JvmField val desiredMiscSelect = field(Int32())
    @JvmField val tcsMinPool = field(Int32())
    @JvmField val enclaveSize = field(Int64())
    @JvmField val attributes = field(SgxAttributes)
    @JvmField val enclaveCss = field(SgxEnclaveCss)
    @JvmField val dirs = field(CArray(SgxDataDirectory, DirIndex.DIR_NUM))
    @JvmField val data = field(FixedBytes(18592))
}

/**
typedef struct _css_header_t {        /* 128 bytes */
    uint8_t  header[12];                /* (0) must be (06000000E100000000000100H) */
    uint32_t type;                      /* (12) bit 31: 0 = prod, 1 = debug; Bit 30-0: Must be zero */
    uint32_t module_vendor;             /* (16) Intel=0x8086, ISV=0x0000 */
    uint32_t date;                      /* (20) build date as yyyymmdd */
    uint8_t  header2[16];               /* (24) must be (01010000600000006000000001000000H) */
    uint32_t hw_version;                /* (40) For Launch Enclaves: HWVERSION != 0. Others, HWVERSION = 0 */
    uint8_t  reserved[84];              /* (44) Must be 0 */
} css_header_t;
 */
object SgxCssHeader : Struct() {
    @JvmField val header = field(FixedBytes(12))
    @JvmField val type = field(Int32())
    @JvmField val moduleVendor = field(SgxModuleVendor)
    @JvmField val date = field(Int32())
    @JvmField val header2 = field(FixedBytes(16))
    @JvmField val hwVersion = field(Int32())
    @JvmField val reserved = field(ReservedBytes(84))
}

object SgxModuleVendor : Enum32() {
    @JvmField val INTEL: Int = 0x8086
    @JvmField val ISV: Int = 0x0000
}

/**
/* Enclave Flags Bit Masks */
#define SGX_FLAGS_INITTED        0x0000000000000001ULL     /* If set, then the enclave is initialized */
#define SGX_FLAGS_DEBUG          0x0000000000000002ULL     /* If set, then the enclave is debug */
#define SGX_FLAGS_MODE64BIT      0x0000000000000004ULL     /* If set, then the enclave is 64 bit */
#define SGX_FLAGS_PROVISION_KEY  0x0000000000000010ULL     /* If set, then the enclave has access to provision key */
#define SGX_FLAGS_EINITTOKEN_KEY 0x0000000000000020ULL     /* If set, then the enclave has access to EINITTOKEN key */
 */
object SgxEnclaveFlags : Flags64() {
    @JvmField val INITTED        = 0x0000000000000001L     /* If set, then the enclave is initialized */
    @JvmField val DEBUG          = 0x0000000000000002L     /* If set, then the enclave is debug */
    @JvmField val MODE64BIT      = 0x0000000000000004L     /* If set, then the enclave is 64 bit */
    @JvmField val PROVISION_KEY  = 0x0000000000000010L     /* If set, then the enclave has access to provision key */
    @JvmField val EINITTOKEN_KEY = 0x0000000000000020L     /* If set, then the enclave has access to EINITTOKEN key */
}

/**
/* XSAVE Feature Request Mask */
#define SGX_XFRM_LEGACY          0x0000000000000003ULL     /* Legacy XFRM which includes the basic feature bits required by SGX, x87 state(0x01) and SSE state(0x02) */
#define SGX_XFRM_AVX             0x0000000000000006ULL     /* AVX XFRM which includes AVX state(0x04) and SSE state(0x02) required by AVX */
#define SGX_XFRM_AVX512          0x00000000000000E6ULL     /* AVX-512 XFRM - not supported */
#define SGX_XFRM_MPX             0x0000000000000018ULL     /* MPX XFRM - not supported */
 */
object SgxXfrmFlags : Flags64() {
    @JvmField val LEGACY_x87     = 0x0000000000000001L
    @JvmField val LEGACY_SSE     = 0x0000000000000002L
    @JvmField val AVX            = 0x0000000000000004L
}

/**
typedef struct _css_key_t {           /* 772 bytes */
    uint8_t modulus[SE_KEY_SIZE];       /* (128) Module Public Key (keylength=3072 bits) */
    uint8_t exponent[SE_EXPONENT_SIZE]; /* (512) RSA Exponent = 3 */
    uint8_t signature[SE_KEY_SIZE];     /* (516) Signature over Header and Body */
} css_key_t;
 */
object SgxCssKey : Struct() {
    @JvmField val modulus = field(FixedBytes(Constants.SE_KEY_SIZE))
    @JvmField val exponent = field(FixedBytes(Constants.SE_EXPONENT_SIZE))
    @JvmField val signature = field(FixedBytes(Constants.SE_KEY_SIZE))
}

/**
typedef struct _css_body_t {            /* 128 bytes */
    sgx_misc_select_t   misc_select;    /* (900) The MISCSELECT that must be set */
    sgx_misc_select_t   misc_mask;      /* (904) Mask of MISCSELECT to enforce */
    uint8_t             reserved[20];   /* (908) Reserved. Must be 0. */
    sgx_attributes_t    attributes;     /* (928) Enclave Attributes that must be set */
    sgx_attributes_t    attribute_mask; /* (944) Mask of Attributes to Enforce */
    sgx_measurement_t   enclave_hash;   /* (960) MRENCLAVE - (32 bytes) */
    uint8_t             reserved2[32];  /* (992) Must be 0 */
    uint16_t            isv_prod_id;    /* (1024) ISV assigned Product ID */
    uint16_t            isv_svn;        /* (1026) ISV assigned SVN */
} css_body_t;
*/
object SgxCssBody : Struct() {
    @JvmField val miscSelect = field(SgxMiscSelect)
    @JvmField val miscMask = field(SgxMiscSelect)
    @JvmField val reserved = field(ReservedBytes(20))
    @JvmField val attributes = field(SgxAttributes)
    @JvmField val attributesMask = field(SgxAttributes)
    @JvmField val measurement = field(SgxMeasurement)
    @JvmField val reserved2 = field(ReservedBytes(32))
    @JvmField val isvProdId = field(SgxIsvProdId)
    @JvmField val isvSvn = field(SgxIsvSvn)
}

/**
typedef struct _css_buffer_t {         /* 780 bytes */
    uint8_t  reserved[12];              /* (1028) Must be 0 */
    uint8_t  q1[SE_KEY_SIZE];           /* (1040) Q1 value for RSA Signature Verification */
    uint8_t  q2[SE_KEY_SIZE];           /* (1424) Q2 value for RSA Signature Verification */
} css_buffer_t;
*/
object SgxCssBuffer : Struct() {
    @JvmField val reserved = field(ReservedBytes(12))
    @JvmField val q1 = field(FixedBytes(Constants.SE_KEY_SIZE))
    @JvmField val q2 = field(FixedBytes(Constants.SE_KEY_SIZE))
}

/**
typedef struct _enclave_css_t {        /* 1808 bytes */
    css_header_t    header;             /* (0) */
    css_key_t       key;                /* (128) */
    css_body_t      body;               /* (900) */
    css_buffer_t    buffer;             /* (1028) */
} enclave_css_t;
 */
object SgxEnclaveCss : Struct() {
    @JvmField val header = field(SgxCssHeader)
    @JvmField val key = field(SgxCssKey)
    @JvmField val body = field(SgxCssBody)
    @JvmField val buffer = field(SgxCssBuffer)
}

/**
typedef struct _data_directory_t
{
    uint32_t    offset;
    uint32_t    size;
} data_directory_t;
*/
object SgxDataDirectory : Struct() {
    @JvmField val offset = field(Int32())
    @JvmField val size = field(Int32())
}

