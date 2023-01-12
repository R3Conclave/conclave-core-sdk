package com.r3.conclave.common.internal.attestation

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonFormat
import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.core.JsonGenerator
import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.databind.*
import com.fasterxml.jackson.databind.annotation.JsonDeserialize
import com.fasterxml.jackson.databind.annotation.JsonSerialize
import com.fasterxml.jackson.databind.deser.std.StdDeserializer
import com.fasterxml.jackson.databind.module.SimpleModule
import com.fasterxml.jackson.databind.ser.std.StdSerializer
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.r3.conclave.common.OpaqueBytes
import com.r3.conclave.common.SHA256Hash
import com.r3.conclave.common.internal.ByteCursor
import com.r3.conclave.common.internal.Cursor
import com.r3.conclave.common.internal.SgxQuote
import com.r3.conclave.common.internal.SgxSignedQuote
import java.lang.IllegalArgumentException
import java.lang.NumberFormatException
import java.time.Instant
import java.util.*
import kotlin.collections.Collection


// We could avoid the redundant usage of @JsonProperty if we used the Kotlin Jackson module. However that makes shading
// Kotlin more difficult and so we just put up with this minor boilerplate.

/**
 * Definitions taken from https://api.trustedservices.intel.com/documents/sgx-attestation-api-spec.pdf.
 *
 * The Attestation Verification Report is a data structure returned by the Attestation Service for Intel® SGX to the
 * Service Provider. It contains a cryptographically signed report of verification of the identity of ISV enclave and
 * the Trusted Computing Base (TCB) of the platform.
 *
 * @property id Representation of unique identifier of the Attestation Verification Report.
 *
 * @property isvEnclaveQuoteBody Body of [SgxSignedQuote] as received by the attestion service.
 *
 * @property timestamp Representation of date and time the Attestation Verification Report was created.
 *
 * @property version Integer that denotes the version of the Verification Attestation Evidence API that has been used to
 * generate the report (currently set to 3). Service Providers should verify this field to confirm that the report was
 * generated by the intended API version, instead of a different API version with potentially different security properties.
 *
 * @property revocationReason Integer corresponding to revocation reason code for a revoked EPID group listed in EPID
 * Group CRL. Allowed values are described in [RFC 5280](https://www.ietf.org/rfc/rfc5280.txt). This field will only be
 * present if value of isvEnclaveQuoteStatus is equal to GROUP_REVOKED.
 *
 * @property pseManifestStatus This field will only be present if the SGX Platform Service Security Property Descriptor
 * (pseManifest) is provided in Attestation Evidence Payload and isvEnclaveQuoteStatus is equal to OK, GROUP_OUT_OF_DATE
 * or CONFIGURATION_NEEDED.
 *
 * @property pseManifestHash SHA-256 calculated over SGX Platform Service Security Property Descriptor as received in
 * Attestation Evidence Payload. This field will only be present if pseManifest field is provided in Attestation Evidence
 * Payload.
 *
 * @property platformInfoBlob A TLV containing an opaque binary blob that the Service Provider and the ISV SGX Application
 * are supposed to forward to SGX Platform SW. This field will only be present if one the following conditions is met:
 * * isvEnclaveQuoteStatus is equal to GROUP_REVOKED, GROUP_OUT_OF_DATE or CONFIGURATION_NEEDED,
 * * pseManifestStatus is equal to one of the following values: OUT_OF_DATE, REVOKED or RL_VERSION_MISMATCH.
 *
 * @property nonce A string that represents a nonce value provided by SP in Attestation Evidence Payload. This field will
 * only be present if nonce field is provided in Attestation Evidence Payload.
 *
 * @property epidPseudonym Byte array representing EPID Pseudonym that consists of the concatenation of EPID B (64 bytes)
 * & EPID K (64 bytes) components of EPID signature. If two linkable EPID signatures for an EPID Group have the same EPID
 * Pseudonym, the two signatures were generated using the same EPID private key. This field will only be present if
 * Attestation Evidence Payload contains Quote with linkable EPID signature.
 *
 * @property advisoryURL URL to Intel® Product Security Center Advisories page that provides additional information on
 * SGX-related security issues. IDs of advisories for specific issues that may affect the attested platform are conveyed
 * by [advisoryIDs].
 *
 * @property advisoryIDs List of advisory IDs that can be searched on the page indicated by [advisoryURL]. Advisory IDs
 * refer to articles providing insight into enclave-related security issues that may affect the attested platform.
 *
 * This is only populated if [isvEnclaveQuoteStatus] is either [EpidQuoteStatus.GROUP_OUT_OF_DATE], [EpidQuoteStatus.CONFIGURATION_NEEDED],
 * [EpidQuoteStatus.SW_HARDENING_NEEDED] or [EpidQuoteStatus.CONFIGURATION_AND_SW_HARDENING_NEEDED].
 */
@JsonInclude(NON_NULL)
data class EpidVerificationReport @JsonCreator constructor(
    @JsonProperty("id")
    val id: String,

    @JsonProperty("isvEnclaveQuoteStatus")
    val isvEnclaveQuoteStatus: EpidQuoteStatus,

    @JsonProperty("isvEnclaveQuoteBody")
    @JsonSerialize(using = SgxQuoteSerializer::class)
    @JsonDeserialize(using = SgxQuoteDeserializer::class)
    val isvEnclaveQuoteBody: ByteCursor<SgxQuote>,

    @JsonProperty("platformInfoBlob")
    val platformInfoBlob: OpaqueBytes? = null,

    @JsonProperty("revocationReason")
    val revocationReason: Int? = null,

    @JsonProperty("pseManifestStatus")
    val pseManifestStatus: ManifestStatus? = null,

    @JsonProperty("pseManifestHash")
    @JsonSerialize(using = ToStringSerializer::class)
    @JsonDeserialize(using = Sha256Deserializer::class)
    val pseManifestHash: SHA256Hash? = null,

    @JsonProperty("nonce")
    val nonce: String? = null,

    @JsonProperty("epidPseudonym")
    @JsonSerialize(using = Base64Serializer::class)
    @JsonDeserialize(using = Base64Deserializer::class)
    val epidPseudonym: OpaqueBytes? = null,

    @JsonProperty("advisoryURL")
    val advisoryURL: String? = null,

    @JsonProperty("advisoryIDs")
    val advisoryIDs: List<String>? = null,

    @JsonProperty("timestamp")
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss.SSSSSS", timezone = "UTC")
    val timestamp: Instant,

    @JsonProperty("version")
    val version: Int
) {
    private class SgxQuoteSerializer : JsonSerializer<ByteCursor<SgxQuote>>() {
        override fun serialize(value: ByteCursor<SgxQuote>, gen: JsonGenerator, provider: SerializerProvider) {
            gen.writeBinary(value.bytes)
        }
    }

    private class SgxQuoteDeserializer : JsonDeserializer<ByteCursor<SgxQuote>>() {
        override fun deserialize(p: JsonParser, ctxt: DeserializationContext): ByteCursor<SgxQuote> {
            return Cursor.wrap(SgxQuote, p.binaryValue)
        }
    }

    private class Sha256Deserializer : StdDeserializer<SHA256Hash>(SHA256Hash::class.java) {
        override fun deserialize(p: JsonParser, ctxt: DeserializationContext): SHA256Hash {
            return SHA256Hash.parse(p.valueAsString)
        }
    }

    private class Base64Serializer : StdSerializer<OpaqueBytes>(OpaqueBytes::class.java) {
        override fun serialize(value: OpaqueBytes, gen: JsonGenerator, provider: SerializerProvider) {
            gen.writeBinary(value.bytes)
        }
    }

    private class Base64Deserializer : StdDeserializer<OpaqueBytes>(OpaqueBytes::class.java) {
        override fun deserialize(p: JsonParser, ctxt: DeserializationContext): OpaqueBytes = OpaqueBytes(p.binaryValue)
    }
}

/**
 * https://api.portal.trustedservices.intel.com/documentation#pcs-tcb-info-v2
 *
 * @property signature Signature calculated over [tcbInfo] body without whitespaces using TCB Signing Key
 * i.e: `{"version":2,"issueDate":"2019-07-30T12:00:00Z","nextUpdate":"2019-08-30T12:00:00Z",...}`
 */
data class SignedTcbInfo(val tcbInfo: TcbInfo, val signature: OpaqueBytes) {
    companion object {
        fun fromJson(json: JsonNode): SignedTcbInfo {
            return SignedTcbInfo(
                    TcbInfo.fromJson(json.getObject("tcbInfo")),
                    json.getHexEncodedBytes("signature")
            )
        }
    }
}

/**
 * The set of technologies that the quote/identity may apply to.
 * This is really more of a type than an "id", but this is the naming used in Intel's code
 */
enum class TypeID {
    SGX,
    TDX
}

/**
 * @property id ID of the tcb info type.
 *
 * @property version Version of the structure.
 *
 * @property issueDate Date and time the TCB information was created.
 *
 * @property nextUpdate Date and time by which next TCB information will be issued.
 *
 * @property fmspc FMSPC (Family-Model-Stepping-Platform-CustomSKU).
 *
 * @property pceId PCE identifier.
 *
 * @property tcbType Type of TCB level composition that determines TCB level comparison logic.
 *
 * @property tcbEvaluationDataNumber A monotonically increasing sequence number changed when Intel updates the content
 * of the TCB evaluation data set: TCB Info, QE Idenity and QVE Identity. The tcbEvaluationDataNumber update is
 * synchronized across TCB Info for all flavors of SGX CPUs (Family-Model-Stepping-Platform-CustomSKU) and QE/QVE Identity.
 * This sequence number allows users to easily determine when a particular TCB Info/QE Idenity/QVE Identiy superseedes
 * another TCB Info/QE Identity/QVE Identity.
 *
 * @property tcbLevels Sorted list of supported TCB levels for given FMSPC.
 */
data class TcbInfo(
    val id: TypeID?,
    val version: Version,
    val issueDate: Instant,
    val nextUpdate: Instant,
    val fmspc: OpaqueBytes,
    val pceId: OpaqueBytes,
    val tcbType: Int,
    val tcbEvaluationDataNumber: Int,
    val tcbLevels: List<TcbLevel>
) {
    /** The TcbInfo json is versioned. We currently support versions 2 and 3. */
    enum class Version(val id: Int) {
        V2(2), V3(3);

        companion object {
            fun fromInt(version: Int): Version {
                return when(version) {
                    2 -> V2
                    3 -> V3
                    else -> throw IllegalArgumentException("Unsupported TcbInfo json version: $version")
                }
            }
        }
    }

    companion object {
        fun fromJson(json: JsonNode): TcbInfo {
            /**
             * First, get the version number.
             * It's not required here, but it is needed for some sub-objects.
             */
            val version = Version.fromInt(json.getInt("version"))

            // ID field is only present in V3 json
            val id = when (version) {
                Version.V2 -> null
                Version.V3 -> TypeID.valueOf(json.getString("id"))
            }

            return TcbInfo(
                    id,
                    version,
                    json.getInstant("issueDate"),
                    json.getInstant("nextUpdate"),
                    json.getHexEncodedBytes("fmspc"),
                    json.getHexEncodedBytes("pceId"),
                    json.getInt("tcbType"),
                    json.getInt("tcbEvaluationDataNumber"),
                    json.getArray("tcbLevels").map { TcbLevel.fromJson(it, version) }
            )
        }
    }
}

/**
 * @property tcbDate Date and time when the TCB level was certified not to be vulnerable to any issues described in SAs
 * that were originally published on or prior to this date.
 *
 * @property tcbStatus TCB level status.
 *
 * @property advisoryIDs Array of Advisory IDs describing vulnerabilities that this TCB level is vulnerable to.

 */
data class TcbLevel(
    val tcb: Tcb,
    val tcbDate: Instant,
    val tcbStatus: TcbStatus,
    val advisoryIDs: List<String>? = null
) {
    companion object {
        fun fromJson(json: JsonNode, version: TcbInfo.Version): TcbLevel {
            return TcbLevel(
                    Tcb.fromJson(json.getObject("tcb"), version),
                    json.getInstant("tcbDate"),
                    TcbStatus.valueOf(json.getString("tcbStatus")),
                    json.getNullable("advisoryIDs") { node ->
                        node.map { it.asText() }
                    }
            )
        }
    }
}

enum class TcbStatus : VerificationStatus {
    /** TCB level of the SGX platform is up-to-date. */
    UpToDate,

    /**
     * TCB level of the SGX platform is up-to-date but due to certain issues affecting the platform, additional SW
     * Hardening in the attesting SGX enclaves may be needed.
     */
    SWHardeningNeeded,

    /**
     * TCB level of the SGX platform is up-to-date but additional configuration of SGX platform may be needed.*/
    ConfigurationNeeded,

    /**
     * TCB level of the SGX platform is up-to-date but additional configuration for the platform and SW Hardening in the
     * attesting SGX enclaves may be needed.
     */
    ConfigurationAndSWHardeningNeeded,

    /** TCB level of SGX platform is outdated. */
    OutOfDate,

    /** TCB level of SGX platform is outdated and additional configuration of SGX platform may be needed. */
    OutOfDateConfigurationNeeded,

    /** TCB level of SGX platform is revoked. The platform is not trustworthy. */
    Revoked
}

/**
 * This class is used for both V2 and V3 APIs.
 */
data class Tcb(
    val sgxtcbcompsvn: List<Int>,
    val pcesvn: Int
) {
    companion object {
        fun fromJson(json: JsonNode, version: TcbInfo.Version): Tcb {
            return when (version) {
                TcbInfo.Version.V2 -> fromJsonV2(json)
                TcbInfo.Version.V3 -> fromJsonV3(json)
            }
        }

        /**
         * Example V2 Json:
         * "tcb": {
         *   "sgxtcbcomp01svn": <int>,
         *   "sgxtcbcomp02svn": <int>,
         *   ...
         *   "pcesvn": <int>
         * }
         */
        private fun fromJsonV2(json: JsonNode): Tcb {
            val sgxtcbcompsvn = ArrayList<Int>()
            for (i in 1..16) {
                val elementString = i.toString().padStart(2, '0')
                sgxtcbcompsvn.add(json.getInt("sgxtcbcomp${elementString}svn"))
            }
            return Tcb(Collections.unmodifiableList(sgxtcbcompsvn), json.getInt("pcesvn"))
        }

        /**
         * Example V3 Json:
         * "tcb": {
         *   "sgxtcbcomponents": [
         *     { "svn": <int> },
         *     { "svn": <int> },
         *     ...
         *   ],
         *   "pcesvn": <int>
         * }
         */
        private fun fromJsonV3(json: JsonNode): Tcb {
            val tcbComponents = json.getArray("sgxtcbcomponents")
            require(tcbComponents.size() == 16) {
                "Unexpected tcb component count, expected 16, got ${tcbComponents.size()}"
            }
            return Tcb(
                    Collections.unmodifiableList(tcbComponents.map { it.getInt("svn") }),
                    json.getInt("pcesvn")
            )
        }
    }
}

/**
 * https://api.portal.trustedservices.intel.com/documentation#pcs-qe-identity-v2
 *
 * @property signature Signature calculated over qeIdentity body (without whitespaces) using TCB Info Signing Key.
 */
data class SignedEnclaveIdentity(
    val enclaveIdentity: EnclaveIdentity,
    val signature: OpaqueBytes
) {
    companion object {
        fun fromJson(json: JsonNode): SignedEnclaveIdentity {
            return SignedEnclaveIdentity(
                    EnclaveIdentity.fromJson(json.getObject("enclaveIdentity")),
                    json.getHexEncodedBytes("signature")
            )
        }
    }
}

/**
 * @property id Identifier of the SGX Enclave issued by Intel. Supported values are QE and QVE.
 *
 * @property version Version of the structure.
 *
 * @property issueDate Date and time the QE Identity information was created.
 *
 * @property nextUpdate Date and time by which next QE identity information will be issued.
 *
 * @property tcbEvaluationDataNumber A monotonically increasing sequence number changed when Intel updates the content
 * of the TCB evaluation data set: TCB Info, QE Idenity and QVE Identity. The tcbEvaluationDataNumber update is
 * synchronized across TCB Info for all flavors of SGX CPUs (Family-Model-Stepping-Platform-CustomSKU) and QE/QVE Identity.
 * This sequence number allows users to easily determine when a particular TCB Info/QE Idenity/QVE Identiy superseedes
 * another TCB Info/QE Identity/QVE Identity.
 *
 * @property miscselect miscselect "golden" value (upon applying mask).
 *
 * @property miscselectMask Mask to be applied to [miscselect] value retrieved from the platform.
 *
 * @property attributes attributes "golden" value (upon applying mask).
 *
 * @property attributesMask Mask to be applied to attributes value retrieved from the platform.
 *
 * @property mrsigner mrsigner hash.
 *
 * @property isvprodid Enclave Product ID.
 *
 * @property tcbLevels Sorted list of supported Enclave TCB levels for given QE.
 */
data class EnclaveIdentity(
    val id: String,
    val version: Version,
    val issueDate: Instant,
    val nextUpdate: Instant,
    val tcbEvaluationDataNumber: Int,
    val miscselect: OpaqueBytes,
    val miscselectMask: OpaqueBytes,
    val attributes: OpaqueBytes,
    val attributesMask: OpaqueBytes,
    val mrsigner: OpaqueBytes,
    val isvprodid: Int,
    val tcbLevels: List<EnclaveTcbLevel>
) {
    /** The EnclaveIdentity json is versioned. We currently only support versions 2. */
    enum class Version(val id: Int) {
        V2(2);

        companion object {
            fun fromInt(version: Int): Version {
                return when(version) {
                    2 -> V2
                    else -> throw IllegalArgumentException("Unsupported EnclaveIdentity json version: $version")
                }
            }
        }
    }

    companion object {
        fun fromJson(json: JsonNode): EnclaveIdentity {
            val version = Version.fromInt(json.getInt("version"))
            return EnclaveIdentity(
                    json.getString("id"),
                    version,
                    json.getInstant("issueDate"),
                    json.getInstant("nextUpdate"),
                    json.getInt("tcbEvaluationDataNumber"),
                    json.getHexEncodedBytes("miscselect"),
                    json.getHexEncodedBytes("miscselectMask"),
                    json.getHexEncodedBytes("attributes"),
                    json.getHexEncodedBytes("attributesMask"),
                    json.getHexEncodedBytes("mrsigner"),
                    json.getInt("isvprodid"),
                    json.getArray("tcbLevels").map { EnclaveTcbLevel.fromJson(it) }
            )
        }
    }
}

/**
 * @property tcbDate Date and time when the TCB level was certified not to be vulnerable to any issues described in SAs
 * that were originally published on or prior to this date.
 *
 * @property tcbStatus TCB level status.
 */
data class EnclaveTcbLevel(
    val tcb: EnclaveTcb,
    val tcbDate: Instant,
    val tcbStatus: EnclaveTcbStatus
) {
    companion object {
        fun fromJson(json: JsonNode): EnclaveTcbLevel {
            return EnclaveTcbLevel(
                    EnclaveTcb.fromJson(json.getObject("tcb")),
                    json.getInstant("tcbDate"),
                    EnclaveTcbStatus.valueOf(json.getString("tcbStatus"))
            )
        }
    }
}

enum class EnclaveTcbStatus {
    /** TCB level of the SGX platform is up-to-date.. */
    UpToDate,

    /** TCB level of SGX platform is outdated. */
    OutOfDate,

    /** TCB level of SGX platform is revoked. The platform is not trustworthy. */
    Revoked
}

/**
 * @property isvsvn SGX Enclave’s ISV SVN.
 */
data class EnclaveTcb(val isvsvn: Int) {
    companion object {
        fun fromJson(json: JsonNode): EnclaveTcb {
            return EnclaveTcb(json.getInt("isvsvn"))
        }
    }
}

/**
 * Utility functions for manual parsing of json primitives and producing sensible error messages.
 */
private fun JsonNode.checkFieldExists(fieldName: String) {
    require(this.has(fieldName)) { "No such field $fieldName." }
}

private fun JsonNode.getInt(fieldName: String): Int {
    checkFieldExists(fieldName)
    val field = this.get(fieldName)
    require(field.isInt) { "Expected $fieldName to be an integer, got ${field.nodeType}." }
    return field.asInt()
}

private fun JsonNode.getString(fieldName: String): String {
    checkFieldExists(fieldName)
    val field = this.get(fieldName)
    require(field.isTextual) { "Expected $fieldName to be a string, got ${field.nodeType}." }
    return field.asText()
}

private fun JsonNode.getHexEncodedBytes(fieldName: String): OpaqueBytes {
    val hexString = getString(fieldName)
    return try {
        OpaqueBytes.parse(hexString)
    } catch (e: IllegalArgumentException) {
        throw IllegalArgumentException("Expected $fieldName to be a valid hex string, got '$hexString'.")
    }
}

private fun JsonNode.getInstant(fieldName: String): Instant {
    val dateString = getString(fieldName)
    return try {
        Instant.parse(dateString)
    } catch (e: Exception) {
        throw IllegalArgumentException("Expected $fieldName to be a date in ISO-8601 format, got $dateString", e)
    }
}

private fun JsonNode.getArray(fieldName: String): JsonNode {
    checkFieldExists(fieldName)
    val field = this.get(fieldName)
    require(field.isArray) { "Expected $fieldName to be an array, got ${field.nodeType}." }
    return field
}

private fun JsonNode.getObject(fieldName: String): JsonNode {
    checkFieldExists(fieldName)
    val field = this.get(fieldName)
    require(field.isObject) { "Expected $fieldName to be an object, got ${field.nodeType}." }
    return field
}

/**
 * Get a nullable json array.
 * Note that this function will treat non-existing fields as "null".
 */
private fun <T> JsonNode.getNullable(fieldName: String, fn: (node: JsonNode) -> T): T? {
    return if (this.has(fieldName) && !this.get(fieldName).isNull) {
        fn(this.get(fieldName))
    } else {
        null
    }
}

val attestationObjectMapper = ObjectMapper().apply {
    registerModule(SimpleModule().apply {
        addDeserializer(OpaqueBytes::class.java, Base16Deserializer())
        addSerializer(OpaqueBytes::class.java, ToStringSerializer.instance)
    })
    registerModule(JavaTimeModule())
}

private class Base16Deserializer : StdDeserializer<OpaqueBytes>(OpaqueBytes::class.java) {
    override fun deserialize(p: JsonParser, ctxt: DeserializationContext): OpaqueBytes {
        return OpaqueBytes.parse(p.valueAsString)
    }
}
