package com.r3.sgx.enclavelethost.ias.schemas

import java.time.Instant

class ReportResponse(
        val id: String,
        val isvEnclaveQuoteStatus: QuoteStatus,
        val isvEnclaveQuoteBody: ByteArray,
        val platformInfoBlob: ByteArray? = null,
        val revocationReason: Int? = null,
        val pseManifestStatus: ManifestStatus? = null,
        val pseManifestHash: ByteArray? = null,
        val nonce: String? = null,
        val epidPseudonym: ByteArray? = null,
        val timestamp: Instant,
        val version: Int
)
