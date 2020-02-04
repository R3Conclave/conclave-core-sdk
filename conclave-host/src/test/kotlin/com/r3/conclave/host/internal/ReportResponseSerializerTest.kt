package com.r3.conclave.host.internal

import com.fasterxml.jackson.databind.ObjectMapper
import com.google.common.io.BaseEncoding
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.ZoneOffset.UTC
import java.time.ZonedDateTime
import java.util.*
import kotlin.random.Random

class ReportResponseSerializerTest {
    private val mapper = ReportResponseSerializer.register(ObjectMapper())

    @Test
    fun `minimum fields`() {
        val isvEnclaveQuoteBody = Random.nextBytes(100)

        val reportResponse = ReportResponse(
                id = "165171271757108173876306223827987629752",
                timestamp = ZonedDateTime.of(2015, 9, 29, 10, 7, 26, 711_023_000, UTC).toInstant(),
                version = 3,
                isvEnclaveQuoteStatus = QuoteStatus.OK,
                isvEnclaveQuoteBody = isvEnclaveQuoteBody
        )

        val toJsonTree = ObjectMapper().readTree(mapper.writeValueAsBytes(reportResponse))
        assertThat(toJsonTree["id"].textValue()).isEqualTo("165171271757108173876306223827987629752")
        assertThat(toJsonTree["timestamp"].textValue()).isEqualTo("2015-09-29T10:07:26.711023")
        assertThat(toJsonTree["version"].intValue()).isEqualTo(3)
        assertThat(toJsonTree["isvEnclaveQuoteStatus"].textValue()).isEqualTo("OK")
        assertThat(toJsonTree["isvEnclaveQuoteBody"].textValue()).isEqualTo(Base64.getEncoder().encodeToString(isvEnclaveQuoteBody))
        assertThat(toJsonTree["platformInfoBlob"]).isNull()
        assertThat(toJsonTree["revocationReason"]).isNull()
        assertThat(toJsonTree["pseManifestStatus"]).isNull()
        assertThat(toJsonTree["pseManifestHash"]).isNull()
        assertThat(toJsonTree["nonce"]).isNull()
        assertThat(toJsonTree["epidPseudonym"]).isNull()
    }

    @Test
    fun `all fields`() {
        val isvEnclaveQuoteBody = Random.nextBytes(100)
        val platformInfoBlob = Random.nextBytes(50)
        val pseManifestHash = Random.nextBytes(32)
        val epidPseudonym = Random.nextBytes(100)

        val reportResponse = ReportResponse(
                id = "165171271757108173876306223827987629752",
                timestamp = ZonedDateTime.of(2015, 9, 29, 10, 7, 26, 711_023_000, UTC).toInstant(),
                version = 3,
                isvEnclaveQuoteStatus = QuoteStatus.OK,
                isvEnclaveQuoteBody = isvEnclaveQuoteBody,
                platformInfoBlob = platformInfoBlob,
                revocationReason = 1,
                pseManifestStatus = ManifestStatus.INVALID,
                pseManifestHash = pseManifestHash,
                nonce = "12345",
                epidPseudonym = epidPseudonym
        )

        val toJsonTree = ObjectMapper().readTree(mapper.writeValueAsBytes(reportResponse))
        assertThat(toJsonTree["id"].textValue()).isEqualTo("165171271757108173876306223827987629752")
        assertThat(toJsonTree["timestamp"].textValue()).isEqualTo("2015-09-29T10:07:26.711023")
        assertThat(toJsonTree["version"].intValue()).isEqualTo(3)
        assertThat(toJsonTree["isvEnclaveQuoteStatus"].textValue()).isEqualTo("OK")
        assertThat(toJsonTree["isvEnclaveQuoteBody"].textValue()).isEqualTo(Base64.getEncoder().encodeToString(isvEnclaveQuoteBody))
        assertThat(toJsonTree["platformInfoBlob"].textValue()).isEqualToIgnoringCase(BaseEncoding.base16().encode(platformInfoBlob))
        assertThat(toJsonTree["revocationReason"].intValue()).isEqualTo(1)
        assertThat(toJsonTree["pseManifestStatus"].textValue()).isEqualTo("INVALID")
        assertThat(toJsonTree["pseManifestHash"].textValue()).isEqualToIgnoringCase(BaseEncoding.base16().encode(pseManifestHash))
        assertThat(toJsonTree["nonce"].textValue()).isEqualTo("12345")
        assertThat(toJsonTree["epidPseudonym"].textValue()).isEqualTo(Base64.getEncoder().encodeToString(epidPseudonym))
    }
}
