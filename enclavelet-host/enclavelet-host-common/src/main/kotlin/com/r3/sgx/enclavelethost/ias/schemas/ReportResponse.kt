package com.r3.sgx.enclavelethost.ias.schemas

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.annotation.JsonPropertyOrder

@JsonPropertyOrder(
    "nonce",
    "id",
    "timestamp",
    "epidPseudonym",
    "isvEnclaveQuoteStatus",
    "isvEnclaveQuoteBody",
    "pseManifestStatus",
    "pseManifestHash",
    "platformInfoBlob",
    "revocationReason"
)
@JsonInclude(NON_NULL)
class ReportResponse(
        @param:JsonProperty("id")
        val id: String,

        @param:JsonProperty("isvEnclaveQuoteStatus")
        val isvEnclaveQuoteStatus: QuoteStatus,

        @param:JsonProperty("isvEnclaveQuoteBody")
        val isvEnclaveQuoteBody: ByteArray,

        @param:JsonProperty("platformInfoBlob")
        val platformInfoBlob: String? = null,

        @param:JsonProperty("revocationReason")
        val revocationReason: Int? = null,

        @param:JsonProperty("pseManifestStatus")
        val pseManifestStatus: ManifestStatus? = null,

        @param:JsonProperty("pseManifestHash")
        val pseManifestHash: String? = null,

        @param:JsonProperty("nonce")
        val nonce: String? = null,

        @param:JsonProperty("epidPseudonym")
        val epidPseudonym: ByteArray? = null,

        @param:JsonProperty("timestamp")
        val timestamp: String,

        @param:JsonProperty("version")
        val version: String
)
