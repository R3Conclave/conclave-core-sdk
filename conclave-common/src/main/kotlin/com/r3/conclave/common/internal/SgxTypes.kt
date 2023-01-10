package com.r3.conclave.common.internal

import com.r3.conclave.common.SHA256Hash
import com.r3.conclave.common.internal.SgxCssKey.modulus
import com.r3.conclave.common.internal.SgxQeCertData.data
import com.r3.conclave.common.internal.SgxQeCertData.type
import com.r3.conclave.common.internal.SgxQuote.signType
import com.r3.conclave.common.internal.SgxSignedQuote.quote
import com.r3.conclave.common.internal.SgxSignedQuote.signature
import com.r3.conclave.common.internal.attestation.AttestationUtils
import com.r3.conclave.common.internal.attestation.AttestationUtils.parseRawEcdsaToDerEncoding
import java.nio.ByteBuffer
import java.security.KeyFactory
import java.security.PublicKey
import java.security.cert.CertPath
import java.security.spec.X509EncodedKeySpec
import java.util.*

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

/**
 * A 128-bit value that is the used to store a derived key from for example the `sgx_get_key` function.
 *
 * @see `sgx_key_128bit_t`
 */
object SgxKey128Bit : FixedBytes(16)

// The sdk has two versions
object SgxQuoteType32 : Enum32() {
    @JvmField
    val UNLINKABLE: Int = SgxQuoteType.UNLINKABLE.value
    @JvmField
    val LINKABLE: Int = SgxQuoteType.LINKABLE.value
}

/**
 * Describes the type of signature in [SgxSignedQuote.signature].
 *
 * @see `sgx_quote_sign_type_t`
 * @see `sgx_ql_attestation_algorithm_id_t`
 */
object SgxQuoteSignType : Enum16() {
    @JvmField
    val EPID_UNLINKABLE: Int = SgxQuoteType.UNLINKABLE.value
    @JvmField
    val EPID_LINKABLE: Int = SgxQuoteType.LINKABLE.value

    /**
     * ECDSA-256-with-P-256 curve
     * @see SgxEcdsa256BitQuoteAuthData
     */
    const val ECDSA_P256 = 2

    /** CDSA-384-with-P-384 curve (Note: currently not supported) */
    const val ECDSA_P384 = 3
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
    @JvmField
    val report = field(SgxReport)
    @JvmField
    val quoteType = field(SgxQuoteType32)
    @JvmField
    val spid = field(SgxSpid)
}

/**
 * sgx_status_t sgx_init_quote(sgx_target_info_t *p_target_info, sgx_epid_group_id_t *p_gid)
 */
object SgxInitQuoteResponse : Struct() {
    @JvmField
    val quotingEnclaveTargetInfo = field(SgxTargetInfo)
    @JvmField
    val epidGroupId = field(SgxEpidGroupId)
}

/**
 * Type for quote used in remote attestation.
 *
 * @see `sgx_quote_t`
 */
object SgxQuote : Struct() {
    /** The version of the quote structure. */
    @JvmField
    val version = field(UInt16())

    /** The indicator of the [SgxSignedQuote.signature] type. */
    @JvmField
    val signType = field(SgxQuoteSignType)

    /** The Intel® EPID group id of the platform belongs to. */
    @JvmField
    val epidGroupId = field(SgxEpidGroupId)

    /** The svn of the QE. */
    @JvmField
    val qeIsvSvn = field(SgxIsvSvn)

    /** The svn of the PCE. */
    @JvmField
    val pceIsvSvn = field(SgxIsvSvn)
    @JvmField
    val extendedEpidGroupId = field(UInt32())

    /** The base name used in sgx_quote. */
    @JvmField
    val basename = field(SgxBasename)

    /** The report body of the application enclave. */
    @JvmField
    val reportBody = field(SgxReportBody)
}

/**
 * Type for quote used in remote attestation.
 *
 * @see `sgx_quote_t`
 */
object SgxSignedQuote : VariableStruct() {
    /** This has been split out into its own because it some circumstances the signature isn't used. */
    @JvmField
    val quote = field(SgxQuote)

    /**
     * Variable-length data containing the signature and supporting data.
     * @see [SgxQuote.signType]
     */
    @JvmField
    val signature = field(UInt32VariableBytes())
}

fun ByteCursor<SgxSignedQuote>.toEcdsaP256AuthData(): ByteCursor<SgxEcdsa256BitQuoteAuthData> {
    check(this[quote][signType].read() == SgxQuoteSignType.ECDSA_P256) {
        "Not a ECDSA-256-with-P-256 auth data."
    }
    return Cursor.slice(SgxEcdsa256BitQuoteAuthData, this[signature].read())
}

/**
 * Enclave attributes definition structure.
 *
 * Note: When specifying an attributes mask used in key derivation, at a minimum the flags that should be set are
 * [SgxEnclaveFlags.INITTED], [SgxEnclaveFlags.DEBUG] and RESERVED bits.
 *
 * @see `sgx_attributes_t`
 */
object SgxAttributes : Struct() {
    @JvmField
    val flags = field(SgxEnclaveFlags)
    @JvmField
    val xfrm = field(SgxXfrmFlags)
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
    @JvmField
    val measurement = field(SgxMeasurement)

    /** Attributes of the target enclave. */
    @JvmField
    val attributes = field(SgxAttributes)

    /** Reserved for future use. Must be set to zero. */
    @Suppress("unused")
    @JvmField
    val reserved1 = field(ReservedBytes(2))

    /** Enclave CONFIGSVN. */
    @JvmField
    val configSvn = field(SgxConfigSvn)

    /** Misc select bits for the target enclave. Reserved for future function extension. */
    @JvmField
    val miscSelect = field(SgxMiscSelect)

    /** Reserved for future use. Must be set to zero. */
    @Suppress("unused")
    @JvmField
    val reserved2 = field(ReservedBytes(8))

    /** Enclave CONFIGID. */
    @JvmField
    val configId = field(SgxConfigId)

    /** Reserved for future use. Must be set to zero. */
    @Suppress("unused")
    @JvmField
    val reserved3 = field(ReservedBytes(384))
}

/**
 * Data structure that contains information about the enclave. This data structure is a part of the `sgx_report_t`
 * structure.
 *
 * @see `sgx_report_body_t`
 */
object SgxReportBody : Struct() {
    /** Security version number of the host system TCB (CPU). */
    @JvmField
    val cpuSvn = field(SgxCpuSvn)

    /** Misc select bits for the target enclave. Reserved for future function extension. */
    @JvmField
    val miscSelect = field(SgxMiscSelect)

    /** Reserved for future use. Must be set to zero. */
    @Suppress("unused")
    @JvmField
    val reserved1 = field(ReservedBytes(12))

    /** ISV assigned Extended Product ID. */
    @JvmField
    val isvExtProdId = field(SgxIsvExtProdId)

    /** Attributes for the enclave. */
    @JvmField
    val attributes = field(SgxAttributes)

    /**
     * Measurement value of the enclave.
     *
     * MRENCLAVE is a unique 256 bit value that identifies the code and data that was loaded into the enclave during the
     * initial launch.
     */
    @JvmField
    val mrenclave = field(SgxMeasurement)

    /** Reserved for future use. Must be set to zero. */
    @Suppress("unused")
    @JvmField
    val reserved2 = field(ReservedBytes(32))

    /**
     * SHA-256 hash value of the public key that signed the enclave.
     *
     * MRSIGNER can be used to allow software to approve of an enclave based on the author rather than maintaining a
     * list of MRENCLAVEs. It is used in key derivation to allow software to create a lineage of an application. By
     * signing multiple enclaves with the same key, the enclaves will share the same keys and data. Combined with
     * security version numbering ([isvSvn]), the author can release multiple versions of an application which can access
     * keys for previous versions, but not future versions of that application.
     *
     * @see [KeyPolicy.MRSIGNER]
     */
    @JvmField
    val mrsigner = field(SgxMeasurement)

    /** Reserved for future use. Must be set to zero. */
    @Suppress("unused")
    @JvmField
    val reserved3 = field(ReservedBytes(32))

    /** The enclave CONFIGID. */
    @JvmField
    val configId = field(SgxConfigId)

    /** ISV Product ID of the enclave. */
    @JvmField
    val isvProdId = field(SgxProdId)

    /** ISV security version number of the enclave. */
    @JvmField
    val isvSvn = field(SgxIsvSvn)

    /** CONFIGSVN field. */
    @JvmField
    val configSvn = field(SgxConfigSvn)

    /** Reserved for future use. Must be set to zero. */
    @Suppress("unused")
    @JvmField
    val reserved4 = field(ReservedBytes(42))

    /** ISV assigned Family ID. */
    @JvmField
    val isvFamilyId = field(SgxIsvFamilyId)

    /** Set of data used for communication between the enclave and the target enclave.*/
    @JvmField
    val reportData = field(SgxReportData)
}

/**
 * Data structure that contains the report information for the enclave. This is the output parameter from the
 * `sgx_create_report` function. This is the input parameter for the `sgx_init_quote` function.
 *
 * @see `sgx_report_t`
 */
object SgxReport : Struct() {
    /** The data structure containing information about the enclave. */
    @JvmField
    val body = field(SgxReportBody)

    /** Value for key wear-out protection. */
    @JvmField
    val keyId = field(SgxKeyId)

    /** The CMAC value of the report data using report key. */
    @JvmField
    val mac = field(SgxMac)
}

/**
 * @see SgxAttributes.flags
 */
object SgxEnclaveFlags : Flags64() {
    /** The enclave is initialized. */
    const val INITTED = 0x0000000000000001L

    /** The enclave is a debug enclave. */
    const val DEBUG = 0x0000000000000002L

    /** The enclave runs in 64 bit mode. */
    const val MODE64BIT = 0x0000000000000004L

    /** The enclave has access to a provision key. */
    const val PROVISION_KEY = 0x0000000000000010L

    /** The enclave has access to a launch key */
    const val EINITTOKEN_KEY = 0x0000000000000020L

    /** The enclave requires the KSS feature. */
    const val KSS = 0x0000000000000080L
}

/**
 * @see SgxAttributes.xfrm
 */
object SgxXfrmFlags : Flags64() {
    /** FPU and Intel® Streaming SIMD Extensions states are saved. */
    const val LEGACY = 0x0000000000000003L

    /** Intel® Advanced Vector Extensions state is saved. */
    const val AVX = 0x0000000000000006L
}

/**
 * Data structure of a key request used for selecting the appropriate key and any additional parameters required in the
 * derivation of the key. This is an input parameter for the `sgx_get_key` function.
 *
 * @see `sgx_key_request_t`
 */
object SgxKeyRequest : Struct() {
    /** The key name requested. */
    @JvmField
    val keyName = field(KeyName)

    /** Identify which inputs are required for the key derivation. */
    @JvmField
    val keyPolicy = field(KeyPolicy)

    /** The ISV security version number that should be used in the key derivation. */
    @JvmField
    val isvSvn = field(SgxIsvSvn)

    /** Reserved for future use. Must be set to zero. */
    @Suppress("unused")
    @JvmField
    val reserved1 = field(ReservedBytes(2))

    /** The TCB security version number that should be used in the key derivation. */
    @JvmField
    val cpuSvn = field(SgxCpuSvn)

    /**
     * Attributes mask used to determine which enclave attributes must be included in the key. It only impacts the
     * derivation of a seal key, a provisioning key, and a provisioning seal key.
     */
    @JvmField
    val attributeMask = field(SgxAttributes)

    /** Value for key wear-out protection. Generally initialized with a random number. */
    @JvmField
    val keyId = field(SgxKeyId)

    /**
     * The misc mask used to determine which enclave misc select must be included in the key. Reserved for future
     * function extension.
     */
    @JvmField
    val miscMask = field(SgxMiscSelect)

    /** The enclave CONFIGSVN field. */
    @JvmField
    val configSvn = field(SgxConfigSvn)

    /** Reserved for future use. Must be set to zero. */
    @Suppress("unused")
    @JvmField
    val reserved2 = field(ReservedBytes(434))
}

/**
 * https://download.01.org/intel-sgx/latest/dcap-latest/linux/docs/Intel_SGX_ECDSA_QuoteLibReference_DCAP_API.pdf, page 47
 * and QuoteVerification/QVL/Src/AttestationLibrary/src/QuoteVerification/Quote.h
 *
 * @see [SgxSignedQuote.signature]
 */
object SgxEcdsa256BitQuoteAuthData : VariableStruct() {
    /** ECDSA signature over [SgxQuote] calculated using [ecdsaAttestationKey]. */
    @JvmField
    val ecdsa256BitSignature = field(SgxEcdsa256BitSignature)

    /** Public part of the ECDSA Attestation Key generated by the Quoting Enclave. */
    @JvmField
    val ecdsaAttestationKey = field(SgxEcdsa256BitPubkey)

    /**
     * Report of the Quoting Enclave that generated the ECDSA Attestation Key.
     *
     * Report Data: SHA256(ECDSA Attestation Key || QE Authentication Data) || 32-0x00’s
     *
     * Note: The QE Report is a report when the QE Report is certified. The CPUSVN and ISVSVN in this report may be
     * older than the currently loaded QE.
     */
    @JvmField
    val qeReport = field(SgxReportBody)

    /** ECDSA signature over [qeReport] calculated using the Provisioning Certification Key */
    @JvmField
    val qeReportSignature = field(SgxEcdsa256BitSignature)

    /**
     * Variable-length data chosen by the Quoting Enclave and signed by the Provisioning Certification Key (as a part of
     * the Report Data in the QE Report). It can be used by the QE to add additional context to the ECDSA Attestation Key
     * utilized by the QE. For example, this may indicate the customer, geography, network, or anything pertinent to the
     * identity of the Quoting Enclave.
     *
     * Size should be set to 0 if there is no additional data.
     */
    @JvmField
    val qeAuthData = field(UInt16VariableBytes())

    /** Data required to verify the QE Report Signature. */
    @JvmField
    val qeCertData = field(SgxQeCertData)
}

/**
 * ECDSA signature, the r component followed by the s component, 2 x 32 bytes.
 */
object SgxEcdsa256BitSignature : FixedBytes(64)

fun ByteCursor<SgxEcdsa256BitSignature>.toDerEncoding(): ByteArray = parseRawEcdsaToDerEncoding(buffer)

/**
 * EC KT-I Public Key, the x-coordinate followedby the y-coordinate (on the RFC 6090 P-256 curve), 2 x 32 bytes.
 */
object SgxEcdsa256BitPubkey : FixedBytes(64)

fun ByteCursor<SgxEcdsa256BitPubkey>.toPublicKey(): PublicKey {
    val encodedKey = ByteBuffer.allocate(P256_HEAD.size + this.size)
    encodedKey.put(P256_HEAD)
    encodedKey.put(this.buffer)
    val keySpec = X509EncodedKeySpec(encodedKey.array())
    return KeyFactory.getInstance("EC").generatePublic(keySpec)
}

// https://stackoverflow.com/questions/30445997/loading-raw-64-byte-long-ecdsa-public-key-in-java
private val P256_HEAD: ByteArray = Base64.getDecoder().decode("MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAE")

object SgxQeCertData : VariableStruct() {
    /**
     * Determines type of data required to verify the QE Report Signature in the Quote Signature Data structure.
     * Supported values:
     *  - 1 (PCK identifier: PPID in plain text, CPUSVN and PCESVN)
     *  - 2 (PCK identifier: PPID encrypted using RSA-2048-OAEP, CPUSVN and PCESVN)
     *  - 3 (PCK identifier: PPID encrypted using RSA-3072-OAEP, CPUSVN and PCESVN)
     *  - 4 (PCK Leaf Certificate in plain text, currently not supported)
     *  - 5 Concatenated PCK Cert Chain
     *  - 7 (PLATFORM_MANIFEST, currently not supported)
     */
    @JvmField
    val type = field(UInt16())

    /** Data required to verify the QE Report Signature depending on the value of [type]. */
    @JvmField
    val data = field(UInt32VariableBytes())
}

fun ByteCursor<SgxQeCertData>.toPckCertPath(): CertPath {
    check(this[type].read() == 5) { "Not a PCK cert path: ${this[type].read()}" }
    // There's a trailing byte which we ignore
    return AttestationUtils.parsePemCertPath(this[data].read(), trailingBytes = 1)
}

/**
 * SgxMetadata structs are sourced from
 * https://github.com/intel/linux-sgx/blob/master/common/inc/internal/metadata.h
 * https://github.com/intel/SGXDataCenterAttestationPrimitives/blob/master/QuoteGeneration/common/inc/internal/linux/arch.h
 */
private const val SE_KEY_SIZE = 384
private const val SE_EXPONENT_SIZE = 4

object SgxMetadataDirectory : Struct() {
    @JvmField
    val directory_offset = field(UInt32())
    @JvmField
    val directory_size = field(UInt32())
}

/**
 * @see `css_header_t`
 */
object SgxCssHeader : Struct() {
    @JvmField
    val header = field(FixedBytes(12))
    @JvmField
    val type = field(UInt32())
    @JvmField
    val moduleVendor = field(UInt32())
    @JvmField
    val date = field(UInt32())
    @JvmField
    val  header2 = field(FixedBytes(16))
    @JvmField
    val hwVersion = field(UInt32())
    @Suppress("unused")
    @JvmField
    val  reserved = field(ReservedBytes(84))
}

/**
 * @see `css_key_t`
 */
object SgxCssKey : Struct() {
    @JvmField
    val modulus = field(FixedBytes(SE_KEY_SIZE))
    @JvmField
    val exponent = field(FixedBytes(SE_EXPONENT_SIZE))
    @JvmField
    val signature = field(FixedBytes(SE_KEY_SIZE))
}

val ByteCursor<SgxCssKey>.mrsigner: SHA256Hash get() = SHA256Hash.hash(this[modulus].bytes)

/**
 * @see `css_body_t`
 */
object SgxCssBody : Struct() {
    @JvmField
    val miscSelect = field(SgxMiscSelect)
    @JvmField
    val miscMask = field(SgxMiscSelect)
    @Suppress("unused")
    @JvmField
    val  reserved1 = field(ReservedBytes(4))
    @JvmField
    val isvFamilyId = field(SgxIsvFamilyId)
    @JvmField
    val attributes = field(SgxAttributes)
    @JvmField
    val attributesMask = field(SgxAttributes)
    @JvmField
    val enclaveHash = field(SgxMeasurement)
    @Suppress("unused")
    @JvmField
    val reserved2 = field(ReservedBytes(16))
    @JvmField
    val IsvExtProdId = field(SgxIsvExtProdId)
    @JvmField
    val IsvProdId = field(SgxProdId)
    @JvmField
    val IsvSvn = field(SgxIsvSvn)
}

/**
 * @see `css_buffer_t`
 */
object SgxCssBuffer : Struct() {
    @Suppress("unused")
    @JvmField
    val reserved = field(ReservedBytes(12))
    @JvmField
    val q1 = field(FixedBytes(SE_KEY_SIZE))
    @JvmField
    val q2 = field(FixedBytes(SE_KEY_SIZE))
}

/**
 * This is also known as the `SIGSTRUCT`.
 *
 * @see `enclave_css_t`
 */
object SgxEnclaveCss : Struct() {
    @JvmField
    val header = field(SgxCssHeader)
    @JvmField
    val key = field(SgxCssKey)
    @JvmField
    val body = field(SgxCssBody)
    @JvmField
    val buffer = field(SgxCssBuffer)
}

object SgxEnclaveMetadata: Struct() {
    private const val DATA_SIZE = 18592

    @JvmField
    val magic = field(Int64()) // uint64
    @JvmField
    val version = field(Int64()) // uint64
    @JvmField
    val metadataSize = field(UInt32())
    @JvmField
    val tcsPolicy = field(UInt32())
    @JvmField
    val ssaFrameSize = field(UInt32())
    @JvmField
    val maxSaveBufferSize = field(UInt32())
    @JvmField
    val desiredMiscSelect = field(UInt32())
    @JvmField
    val tcsMinPool = field(UInt32())
    @JvmField
    val enclaveSize = field(Int64()) // uint64
    @JvmField
    val attributes = field(SgxAttributes)
    @JvmField
    val enclaveCss = field(SgxEnclaveCss)
    @JvmField
    val dataDirectory = field(SgxMetadataDirectory)
    @JvmField
    val dataDirectory2 = field(SgxMetadataDirectory)
    @JvmField
    val data = field(FixedBytes(DATA_SIZE))
}
