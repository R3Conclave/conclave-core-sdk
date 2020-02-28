package com.r3.sgx.djvm.asserters

import com.google.protobuf.Int32Value
import com.google.protobuf.Int64Value
import com.r3.sgx.djvm.auxiliary.JavaTimeTest.OFFSET_SECONDS
import com.r3.sgx.test.assertion.TestAsserter
import org.assertj.core.api.Assertions.assertThat
import java.time.*
import java.util.*

class JavaTimeTest {
    class InstantTest : TestAsserter {
        override fun assertResult(testResult: ByteArray) {
            val result = String(testResult)
            assertThat(result).isEqualTo(Instant.ofEpochSecond(1L, 1L).toString())
        }
    }

    class TemporalToString : TestAsserter {
        override fun assertResult(testResult: ByteArray) {
            val result = String(testResult)
            assertThat(result).isEqualTo(Instant.ofEpochSecond(1L, 1L).toString())
        }
    }

    class DurationTest : TestAsserter {
        override fun assertResult(testResult: ByteArray) {
            val result = String(testResult)
            assertThat(result).isEqualTo(Duration.ofHours(2).toString())
        }

    }

    class LocalDateTest : TestAsserter {
        override fun assertResult(testResult: ByteArray) {
            val result = String(testResult)
            assertThat(result).isEqualTo(LocalDate.of(1971, 2, 3).toString())
        }

    }

    class LocalTimeTest : TestAsserter {
        override fun assertResult(testResult: ByteArray) {
            val result = String(testResult)
            assertThat(result).isEqualTo(LocalTime.of(1, 2, 3).toString())
        }
    }

    class LocalDateTimeTest : TestAsserter {
        override fun assertResult(testResult: ByteArray) {
            val result = String(testResult)
            assertThat(result).isEqualTo(LocalDateTime.of(1971, 2, 3, 4, 5, 6, 7).toString())
        }
    }

    class MonthDayTest : TestAsserter {
        override fun assertResult(testResult: ByteArray) {
            val result = String(testResult)
            assertThat(result).isEqualTo(MonthDay.of(1, 2).toString())
        }
    }

    class OffsetDateTimeTest : TestAsserter {
        override fun assertResult(testResult: ByteArray) {
            val result = String(testResult)
            assertThat(result).isEqualTo(OffsetDateTime.of(LocalDateTime.of(1971, 2, 3, 4, 5, 6, 7),
                    ZoneOffset.ofHours(7))
                    .toString())
        }
    }

    class OffsetTimeTest : TestAsserter {
        override fun assertResult(testResult: ByteArray) {
            val result = String(testResult)
            assertThat(result).isEqualTo(OffsetTime.of(LocalTime.of(1, 2, 3), ZoneOffset.ofHours(7)).toString())
        }
    }

    class PeriodTest : TestAsserter {
        override fun assertResult(testResult: ByteArray) {
            val result = String(testResult)
            assertThat(result).isEqualTo(Period.of(1, 2, 3).toString())
        }
    }

    class YearTest : TestAsserter {
        override fun assertResult(testResult: ByteArray) {
            val result = String(testResult)
            assertThat(result).isEqualTo(Year.of(1971).toString())
        }
    }

    class YearMonthTest : TestAsserter {
        override fun assertResult(testResult: ByteArray) {
            val result = String(testResult)
            assertThat(result).isEqualTo(YearMonth.of(1971, 2).toString())
        }
    }

    class ZonedDateTimeTest : TestAsserter {
        override fun assertResult(testResult: ByteArray) {
            val result = String(testResult)
            assertThat(result).isEqualTo(ZonedDateTime.of(
                    LocalDateTime.of(1971, 2, 3, 4, 5, 6, 7), ZoneId.of("UTC")
            ).toString())
        }
    }

    class ZoneOffsetTest : TestAsserter {
        override fun assertResult(testResult: ByteArray) {
            val result = String(testResult)
            assertThat(result).isEqualTo(ZoneOffset.ofHours(7).toString())
        }
    }

    class AllZoneIDsTest : TestAsserter {
        override fun assertResult(testResult: ByteArray) {
            val result = Int32Value.parseFrom(testResult).value
            assertThat(result).isEqualTo(600)
        }
    }

    class DefaultZoneIDTest : TestAsserter {
        override fun assertResult(testResult: ByteArray) {
            val result = String(testResult)
            assertThat(result).isEqualTo("UTC")
        }
    }

    class DefaultTimeZoneTest : TestAsserter {
        override fun assertResult(testResult: ByteArray) {
            val result = String(testResult)
            assertThat(result).isEqualTo("Coordinated Universal Time")
        }
    }

    class DateTest : TestAsserter {
        override fun assertResult(testResult: ByteArray) {
            val result = String(testResult)
            assertThat(result).isEqualTo(Date(1).toString())
        }
    }

    class ReturningDateTest : TestAsserter {
        override fun assertResult(testResult: ByteArray) {
            val date = Date(1)
            val later = Date(date.time + OFFSET_SECONDS)

            val result = Int64Value.parseFrom(testResult).value
            assertThat(result).isEqualTo(later.time)
        }
    }
}