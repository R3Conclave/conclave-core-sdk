package com.r3.conclave.common.internal.attestation

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.r3.conclave.common.OpaqueBytes
import com.r3.conclave.common.SHA256Hash
import com.r3.conclave.common.internal.Cursor
import com.r3.conclave.common.internal.SgxQuote
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import java.io.IOException
import java.time.Instant
import java.time.ZoneOffset.UTC
import java.time.ZonedDateTime
import java.util.*
import kotlin.random.Random


private val mapper = ObjectMapper()

/** Load a resource as text, throw if the resource is missing. */
fun loadResourceAsJson(path: String): JsonNode {
    val text = SignedTcbInfoTest::class.java.getResource(path)?.readText() ?: throw IOException("Missing resource: $path")
    return mapper.readTree(text)
}

class EpidVerificationReportTest {
    @Test
    fun `serialise minimum fields`() {
        val isvEnclaveQuoteBody = Cursor.wrap(SgxQuote, Random.nextBytes(432))

        val report = EpidVerificationReport(
            id = "165171271757108173876306223827987629752",
            timestamp = ZonedDateTime.of(2015, 9, 29, 10, 7, 26, 711_023_000, UTC).toInstant(),
            version = 3,
            isvEnclaveQuoteStatus = EpidQuoteStatus.OK,
            isvEnclaveQuoteBody = isvEnclaveQuoteBody
        )

        val jsonTree = ObjectMapper().readTree(attestationObjectMapper.writeValueAsBytes(report))
        assertThat(jsonTree["id"].textValue()).isEqualTo("165171271757108173876306223827987629752")
        assertThat(jsonTree["timestamp"].textValue()).isEqualTo("2015-09-29T10:07:26.711023")
        assertThat(jsonTree["version"].intValue()).isEqualTo(3)
        assertThat(jsonTree["isvEnclaveQuoteStatus"].textValue()).isEqualTo("OK")
        assertThat(jsonTree["isvEnclaveQuoteBody"].textValue()).isEqualTo(
            Base64.getEncoder().encodeToString(isvEnclaveQuoteBody.bytes)
        )
        assertThat(jsonTree["platformInfoBlob"]).isNull()
        assertThat(jsonTree["revocationReason"]).isNull()
        assertThat(jsonTree["pseManifestStatus"]).isNull()
        assertThat(jsonTree["pseManifestHash"]).isNull()
        assertThat(jsonTree["nonce"]).isNull()
        assertThat(jsonTree["epidPseudonym"]).isNull()
    }

    @Test
    fun `deserialise minimum fields`() {
        val isvEnclaveQuoteBody = Cursor.wrap(SgxQuote, Random.nextBytes(432))

        val json = """
            {
              "id":"165171271757108173876306223827987629752",
              "timestamp":"2015-09-29T10:07:26.711023",
              "version":3,
              "isvEnclaveQuoteStatus":"OK",
              "isvEnclaveQuoteBody":"${Base64.getEncoder().encodeToString(isvEnclaveQuoteBody.bytes)}"
            }
            """.trimIndent()


        val report = attestationObjectMapper.readValue(json, EpidVerificationReport::class.java)
        assertThat(report.id).isEqualTo("165171271757108173876306223827987629752")
        assertThat(report.timestamp).isEqualTo(ZonedDateTime.of(2015, 9, 29, 10, 7, 26, 711_023_000, UTC).toInstant())
        assertThat(report.version).isEqualTo(3)
        assertThat(report.isvEnclaveQuoteStatus).isEqualTo(EpidQuoteStatus.OK)
        assertThat(report.isvEnclaveQuoteBody).isEqualTo(isvEnclaveQuoteBody)
    }

    @Test
    fun `serialise all fields`() {
        val isvEnclaveQuoteBody = Cursor.wrap(SgxQuote, Random.nextBytes(432))
        val platformInfoBlob = OpaqueBytes(Random.nextBytes(50))
        val pseManifestHash = SHA256Hash.wrap(Random.nextBytes(32))
        val epidPseudonym = OpaqueBytes(Random.nextBytes(100))

        val report = EpidVerificationReport(
            id = "165171271757108173876306223827987629752",
            timestamp = ZonedDateTime.of(2015, 9, 29, 10, 7, 26, 711_023_000, UTC).toInstant(),
            version = 3,
            isvEnclaveQuoteStatus = EpidQuoteStatus.OK,
            isvEnclaveQuoteBody = isvEnclaveQuoteBody,
            platformInfoBlob = platformInfoBlob,
            revocationReason = 1,
            pseManifestStatus = ManifestStatus.INVALID,
            pseManifestHash = pseManifestHash,
            nonce = "12345",
            epidPseudonym = epidPseudonym
        )

        val jsonTree = ObjectMapper().readTree(attestationObjectMapper.writeValueAsBytes(report))
        assertThat(jsonTree["id"].textValue()).isEqualTo("165171271757108173876306223827987629752")
        assertThat(jsonTree["timestamp"].textValue()).isEqualTo("2015-09-29T10:07:26.711023")
        assertThat(jsonTree["version"].intValue()).isEqualTo(3)
        assertThat(jsonTree["isvEnclaveQuoteStatus"].textValue()).isEqualTo("OK")
        assertThat(jsonTree["isvEnclaveQuoteBody"].textValue()).isEqualTo(
            Base64.getEncoder().encodeToString(isvEnclaveQuoteBody.bytes)
        )
        assertThat(jsonTree["platformInfoBlob"].textValue()).isEqualToIgnoringCase(platformInfoBlob.toString())
        assertThat(jsonTree["revocationReason"].intValue()).isEqualTo(1)
        assertThat(jsonTree["pseManifestStatus"].textValue()).isEqualTo("INVALID")
        assertThat(jsonTree["pseManifestHash"].textValue()).isEqualToIgnoringCase(pseManifestHash.toString())
        assertThat(jsonTree["nonce"].textValue()).isEqualTo("12345")
        assertThat(jsonTree["epidPseudonym"].textValue()).isEqualTo(
            Base64.getEncoder().encodeToString(epidPseudonym.bytes)
        )
    }

    @Test
    fun `deserialise all fields`() {
        val isvEnclaveQuoteBody = Cursor.wrap(SgxQuote, Random.nextBytes(432))
        val platformInfoBlob = OpaqueBytes(Random.nextBytes(50))
        val pseManifestHash = SHA256Hash.wrap(Random.nextBytes(32))
        val epidPseudonym = OpaqueBytes(Random.nextBytes(100))

        val json = """
            {
              "id":"165171271757108173876306223827987629752",
              "timestamp":"2015-09-29T10:07:26.711023",
              "version":3,
              "isvEnclaveQuoteStatus":"OK",
              "isvEnclaveQuoteBody":"${Base64.getEncoder().encodeToString(isvEnclaveQuoteBody.bytes)}",
              "platformInfoBlob":"$platformInfoBlob",
              "revocationReason":1,
              "pseManifestStatus":"INVALID",
              "pseManifestHash":"$pseManifestHash",
              "nonce":"12345",
              "epidPseudonym":"${Base64.getEncoder().encodeToString(epidPseudonym.bytes)}"
            }
            """.trimIndent()

        val report = attestationObjectMapper.readValue(json, EpidVerificationReport::class.java)
        assertThat(report.id).isEqualTo("165171271757108173876306223827987629752")
        assertThat(report.timestamp).isEqualTo(ZonedDateTime.of(2015, 9, 29, 10, 7, 26, 711_023_000, UTC).toInstant())
        assertThat(report.version).isEqualTo(3)
        assertThat(report.isvEnclaveQuoteStatus).isEqualTo(EpidQuoteStatus.OK)
        assertThat(report.isvEnclaveQuoteBody).isEqualTo(isvEnclaveQuoteBody)
        assertThat(report.platformInfoBlob).isEqualTo(platformInfoBlob)
        assertThat(report.revocationReason).isEqualTo(1)
        assertThat(report.pseManifestStatus).isEqualTo(ManifestStatus.INVALID)
        assertThat(report.pseManifestHash).isEqualTo(pseManifestHash)
        assertThat(report.nonce).isEqualTo("12345")
        assertThat(report.epidPseudonym).isEqualTo(epidPseudonym)
    }
}


class SignedTcbInfoTest {
    companion object {
        @JvmStatic
        val getTcbInfoVersions = listOf(TcbInfo.Version.V2, TcbInfo.Version.V3)
    }

    @ParameterizedTest
    @MethodSource("getGetTcbInfoVersions")
    fun `tcb deserialization test`(version: TcbInfo.Version) {
        val json = loadResourceAsJson("test_tcb_${version}.json")
        val result = Tcb.fromJson(json, version)

        for (i in 0 until 16) {
            assertThat(result.sgxtcbcompsvn[i]).isEqualTo(i + 1)
        }

        assertThat(result.pcesvn).isEqualTo(9)
    }

    @ParameterizedTest
    @MethodSource("getGetTcbInfoVersions")
    fun `signature deserialization test`(version: TcbInfo.Version) {
        val json = loadResourceAsJson("test_tcbinfo_${version}.json")
        val result = SignedTcbInfo.fromJson(json)
        assertThat(result.signature).isEqualTo(OpaqueBytes.parse("01020304"))   // Matches resource content
    }

    @ParameterizedTest
    @MethodSource("getGetTcbInfoVersions")
    fun `tcb info deserialization test`(version: TcbInfo.Version) {
        val json = loadResourceAsJson("test_tcbinfo_$version.json")
        val result = SignedTcbInfo.fromJson(json)
        assertThat(result.signature).isEqualTo(OpaqueBytes.parse("01020304"))
        assertThat(result.tcbInfo.version).isEqualTo(version.id)
        assertThat(result.tcbInfo.issueDate).isEqualTo(Instant.parse("2020-01-02T03:04:05Z"))
        assertThat(result.tcbInfo.nextUpdate).isEqualTo(Instant.parse("2021-02-03T04:05:06Z"))
    }
}

class SignedEnclaveIdentityTest {
    companion object {
        @JvmStatic
        val enclaveIdentityVersions = listOf(EnclaveIdentity.Version.V2)
    }

    @ParameterizedTest
    @MethodSource("getEnclaveIdentityVersions")
    fun `signature deserializes correctly`(version: EnclaveIdentity.Version) {
        val json = loadResourceAsJson("test_signed_enclave_identity_$version.json")
        val result = SignedEnclaveIdentity.fromJson(json)
        assertThat(result.signature).isEqualTo(OpaqueBytes.parse("01020304"))
    }
}
