package com.r3.conclave.common.internal.attestation

import com.fasterxml.jackson.databind.ObjectMapper
import com.r3.conclave.common.OpaqueBytes
import com.r3.conclave.common.SHA256Hash
import com.r3.conclave.common.internal.Cursor
import com.r3.conclave.common.internal.SgxQuote
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.ZoneOffset.UTC
import java.time.ZonedDateTime
import java.util.*
import kotlin.random.Random

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
        assertThat(jsonTree["isvEnclaveQuoteBody"].textValue()).isEqualTo(Base64.getEncoder().encodeToString(isvEnclaveQuoteBody.bytes))
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
        assertThat(jsonTree["isvEnclaveQuoteBody"].textValue()).isEqualTo(Base64.getEncoder().encodeToString(isvEnclaveQuoteBody.bytes))
        assertThat(jsonTree["platformInfoBlob"].textValue()).isEqualToIgnoringCase(platformInfoBlob.toString())
        assertThat(jsonTree["revocationReason"].intValue()).isEqualTo(1)
        assertThat(jsonTree["pseManifestStatus"].textValue()).isEqualTo("INVALID")
        assertThat(jsonTree["pseManifestHash"].textValue()).isEqualToIgnoringCase(pseManifestHash.toString())
        assertThat(jsonTree["nonce"].textValue()).isEqualTo("12345")
        assertThat(jsonTree["epidPseudonym"].textValue()).isEqualTo(Base64.getEncoder().encodeToString(epidPseudonym.bytes))
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
    @Test
    fun `deserialise tcb`() {
        val json = """
{
  "sgxtcbcomp01svn":1,
  "sgxtcbcomp02svn":2,
  "sgxtcbcomp03svn":3,
  "sgxtcbcomp04svn":4,
  "sgxtcbcomp05svn":5,
  "sgxtcbcomp06svn":6,
  "sgxtcbcomp07svn":7,
  "sgxtcbcomp08svn":8,
  "sgxtcbcomp09svn":9,
  "sgxtcbcomp10svn":10,
  "sgxtcbcomp11svn":11,
  "sgxtcbcomp12svn":12,
  "sgxtcbcomp13svn":13,
  "sgxtcbcomp14svn":14,
  "sgxtcbcomp15svn":15,
  "sgxtcbcomp16svn":16,
  "pcesvn":9
}
            """.trimIndent()
        val result = attestationObjectMapper.readValue(json, Tcb::class.java)
        assertThat(1).isEqualTo(result.sgxtcbcomp01svn)
    }

    @Test
    fun `deserialise signature`() {
        val signature = "01020304"
        val json = """
            {
              "tcbInfo":{"version":2,"issueDate":"2020-01-02T03:04:05Z","nextUpdate":"2021-02-03T04:05:06Z","fmspc":"00906ed50000","pceId":"0000","tcbType":0,"tcbEvaluationDataNumber":7,
                "tcbLevels":[]
              },
              "signature":"$signature"
            }
            """.trimIndent()
        val result = attestationObjectMapper.readValue(json, SignedTcbInfo::class.java)
        assertThat(result.signature).isEqualTo(OpaqueBytes.parse(signature))
    }

    @Test
    fun `deserialise tcb info`() {
        val signature = "2fcfea244996d64794c3729acff632887de67722cfca7b0458464a74d4101d01879fe28fa01594f28c6e0e97e9558ff0a45898bd6af275e8edffc2364780fe06"
        val json = """
{
"tcbInfo":{
    "version":2,"issueDate":"2020-01-02T03:04:05Z","nextUpdate":"2021-02-03T04:05:06Z","fmspc":"00906ed50000","pceId":"0000","tcbType":0,"tcbEvaluationDataNumber":7,
    "tcbLevels":[
        {"tcb":{"sgxtcbcomp01svn":1,"sgxtcbcomp02svn":2,"sgxtcbcomp03svn":3,"sgxtcbcomp04svn":4,"sgxtcbcomp05svn":5,"sgxtcbcomp06svn":6,"sgxtcbcomp07svn":7,"sgxtcbcomp08svn":8,"sgxtcbcomp09svn":9,"sgxtcbcomp10svn":10,"sgxtcbcomp11svn":11,"sgxtcbcomp12svn":12,"sgxtcbcomp13svn":13,"sgxtcbcomp14svn":14,"sgxtcbcomp15svn":15,"sgxtcbcomp16svn":16,"pcesvn":9},"tcbDate":"2019-11-13T00:00:00Z","tcbStatus":"UpToDate"},
        {"tcb":{"sgxtcbcomp01svn":13,"sgxtcbcomp02svn":13,"sgxtcbcomp03svn":2,"sgxtcbcomp04svn":4,"sgxtcbcomp05svn":1,"sgxtcbcomp06svn":128,"sgxtcbcomp07svn":0,"sgxtcbcomp08svn":0,"sgxtcbcomp09svn":0,"sgxtcbcomp10svn":0,"sgxtcbcomp11svn":0,"sgxtcbcomp12svn":0,"sgxtcbcomp13svn":0,"sgxtcbcomp14svn":0,"sgxtcbcomp15svn":0,"sgxtcbcomp16svn":0,"pcesvn":9},"tcbDate":"2019-11-13T00:00:00Z","tcbStatus":"ConfigurationNeeded"},
        {"tcb":{"sgxtcbcomp01svn":2,"sgxtcbcomp02svn":2,"sgxtcbcomp03svn":2,"sgxtcbcomp04svn":4,"sgxtcbcomp05svn":1,"sgxtcbcomp06svn":128,"sgxtcbcomp07svn":0,"sgxtcbcomp08svn":0,"sgxtcbcomp09svn":0,"sgxtcbcomp10svn":0,"sgxtcbcomp11svn":0,"sgxtcbcomp12svn":0,"sgxtcbcomp13svn":0,"sgxtcbcomp14svn":0,"sgxtcbcomp15svn":0,"sgxtcbcomp16svn":0,"pcesvn":7},"tcbDate":"2019-05-15T00:00:00Z","tcbStatus":"OutOfDate"},
        {"tcb":{"sgxtcbcomp01svn":1,"sgxtcbcomp02svn":1,"sgxtcbcomp03svn":2,"sgxtcbcomp04svn":4,"sgxtcbcomp05svn":1,"sgxtcbcomp06svn":128,"sgxtcbcomp07svn":0,"sgxtcbcomp08svn":0,"sgxtcbcomp09svn":0,"sgxtcbcomp10svn":0,"sgxtcbcomp11svn":0,"sgxtcbcomp12svn":0,"sgxtcbcomp13svn":0,"sgxtcbcomp14svn":0,"sgxtcbcomp15svn":0,"sgxtcbcomp16svn":0,"pcesvn":7},"tcbDate":"2019-01-09T00:00:00Z","tcbStatus":"OutOfDate"},
        {"tcb":{"sgxtcbcomp01svn":1,"sgxtcbcomp02svn":1,"sgxtcbcomp03svn":2,"sgxtcbcomp04svn":4,"sgxtcbcomp05svn":1,"sgxtcbcomp06svn":128,"sgxtcbcomp07svn":0,"sgxtcbcomp08svn":0,"sgxtcbcomp09svn":0,"sgxtcbcomp10svn":0,"sgxtcbcomp11svn":0,"sgxtcbcomp12svn":0,"sgxtcbcomp13svn":0,"sgxtcbcomp14svn":0,"sgxtcbcomp15svn":0,"sgxtcbcomp16svn":0,"pcesvn":6},"tcbDate":"2018-08-15T00:00:00Z","tcbStatus":"OutOfDate"}
    ]
    },
"signature":"$signature"
}
        """.trimIndent()
        val result = attestationObjectMapper.readValue(json, SignedTcbInfo::class.java)
        assertThat(result.signature).isEqualTo(OpaqueBytes.parse(signature))
        assertThat(result.tcbInfo.version).isEqualTo(2)
        assertThat(result.tcbInfo.issueDate).isEqualTo(Instant.parse("2020-01-02T03:04:05Z"))
        assertThat(result.tcbInfo.nextUpdate).isEqualTo(Instant.parse("2021-02-03T04:05:06Z"))
    }
}

class SignedEnclaveIdentityTest {
    @Test
    fun `deserialise signature`() {
        val signature = "01020304"
        val json = """
{
    "enclaveIdentity":{
        "id":"QE",
        "version":2,
        "issueDate":"2019-09-05T07:47:08Z",
        "nextUpdate":"2029-09-05T07:47:08Z",
        "tcbEvaluationDataNumber":0,
        "miscselect":"D182B18C",
        "miscselectMask":"FFFFFFFF",
        "attributes":"70C8CBF48BD76EAB9C8126CE95E96C90",
        "attributesMask":"FFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFF",
        "mrsigner":"8C4F5775D796503E96137F77C68A829A0056AC8DED70140B081B094490C57BFF",
        "isvprodid":1,
        "tcbLevels":[
            {"tcb":{"isvsvn":1},"tcbDate":"2019-09-01T00:00:00Z","tcbStatus":"UpToDate"}
            ]
        },
    "signature":"$signature"
}
        """.trimIndent()
        val result = attestationObjectMapper.readValue(json, SignedEnclaveIdentity::class.java)
        assertThat(result.signature).isEqualTo(OpaqueBytes.parse(signature))
    }
}