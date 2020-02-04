package com.r3.sgx.enclavelethost.client

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.google.common.io.BaseEncoding
import com.r3.conclave.host.internal.ManifestStatus
import com.r3.conclave.host.internal.QuoteStatus
import com.r3.conclave.host.internal.ReportResponse
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test
import java.time.ZoneOffset.UTC
import java.time.ZonedDateTime
import java.util.*
import kotlin.random.Random

class ReportResponseDeserializerTest {
    private val mapper = ReportResponseDeserializer.register(ObjectMapper())

    @Test
    fun `minimum JSON fields`() {
        val isvEnclaveQuoteBody = Random.nextBytes(100)

        val json = """
            {
              "id":"165171271757108173876306223827987629752",
              "timestamp":"2015-09-29T10:07:26.711023",
              "version":3,
              "isvEnclaveQuoteStatus":"OK",
              "isvEnclaveQuoteBody":"${Base64.getEncoder().encodeToString(isvEnclaveQuoteBody)}"
            }
            """.trimIndent()


        val reportResponse = mapper.readValue<ReportResponse>(json)
        assertThat(reportResponse.id).isEqualTo("165171271757108173876306223827987629752")
        assertThat(reportResponse.timestamp).isEqualTo(ZonedDateTime.of(2015, 9, 29, 10, 7, 26, 711_023_000, UTC).toInstant())
        assertThat(reportResponse.version).isEqualTo(3)
        assertThat(reportResponse.isvEnclaveQuoteStatus).isEqualTo(QuoteStatus.OK)
        assertThat(reportResponse.isvEnclaveQuoteBody).isEqualTo(isvEnclaveQuoteBody)
    }

    @Test
    fun `all JSON fields`() {
        val isvEnclaveQuoteBody = Random.nextBytes(100)
        val platformInfoBlob = Random.nextBytes(50)
        val pseManifestHash = Random.nextBytes(32)
        val epidPseudonym = Random.nextBytes(100)

        val json = """
            {
              "id":"165171271757108173876306223827987629752",
              "timestamp":"2015-09-29T10:07:26.711023",
              "version":3,
              "isvEnclaveQuoteStatus":"OK",
              "isvEnclaveQuoteBody":"${Base64.getEncoder().encodeToString(isvEnclaveQuoteBody)}",
              "platformInfoBlob":"${BaseEncoding.base16().encode(platformInfoBlob).toUpperCase()}",
              "revocationReason":1,
              "pseManifestStatus":"INVALID",
              "pseManifestHash":"${BaseEncoding.base16().encode(pseManifestHash).toUpperCase()}",
              "nonce":"12345",
              "epidPseudonym":"${Base64.getEncoder().encodeToString(epidPseudonym)}"
            }
            """.trimIndent()

        val reportResponse = mapper.readValue<ReportResponse>(json)
        assertThat(reportResponse.id).isEqualTo("165171271757108173876306223827987629752")
        assertThat(reportResponse.timestamp).isEqualTo(ZonedDateTime.of(2015, 9, 29, 10, 7, 26, 711_023_000, UTC).toInstant())
        assertThat(reportResponse.version).isEqualTo(3)
        assertThat(reportResponse.isvEnclaveQuoteStatus).isEqualTo(QuoteStatus.OK)
        assertThat(reportResponse.isvEnclaveQuoteBody).isEqualTo(isvEnclaveQuoteBody)
        assertThat(reportResponse.platformInfoBlob).isEqualTo(platformInfoBlob)
        assertThat(reportResponse.revocationReason).isEqualTo(1)
        assertThat(reportResponse.pseManifestStatus).isEqualTo(ManifestStatus.INVALID)
        assertThat(reportResponse.pseManifestHash).isEqualTo(pseManifestHash)
        assertThat(reportResponse.nonce).isEqualTo("12345")
        assertThat(reportResponse.epidPseudonym).isEqualTo(epidPseudonym)
    }
}
