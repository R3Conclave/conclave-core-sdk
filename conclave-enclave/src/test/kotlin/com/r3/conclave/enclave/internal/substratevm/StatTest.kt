package com.r3.conclave.enclave.internal.substratevm

import com.r3.conclave.enclave.internal.substratevm.mock.MockCCharPointer
import com.r3.conclave.enclave.internal.substratevm.mock.MockCIntPointer
import com.r3.conclave.enclave.internal.substratevm.mock.MockStat64
import com.r3.conclave.enclave.internal.substratevm.mock.MockTimeSpec
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Paths
import java.time.Instant

class StatTest : JimfsTest() {
    companion object {
        private const val MARGIN_IN_SECONDS = 2
        private const val WRITE_TIME_GAP_IN_MILLISECONDS = 2000L
        private const val VERSION = 0
    }

    private lateinit var writeTime: Instant
    private val path = Paths.get(file)

    @BeforeEach
    fun writeNewFile() {
        writeTime = Instant.now()!!
        Files.write(path, data)
    }

    @Test
    fun nonExistingFile() {
        val stat = MockStat64()
        val errno = MockCIntPointer()
        val ret = Stat.xstat64(null, VERSION, MockCCharPointer("/non-existing-file"), stat, errno)
        assertThat(ret).isEqualTo(-1)
        assertThat(errno.value).isEqualTo(ErrnoBase.ENOENT)
    }

    @Test
    fun emptyFilePath() {
        val stat = MockStat64()
        val errno = MockCIntPointer()
        val ret = Stat.xstat64(null, VERSION, MockCCharPointer(""), stat, errno)
        assertThat(ret).isEqualTo(-1)
        assertThat(errno.value).isEqualTo(ErrnoBase.ENOENT)
    }

    @Test
    fun relativeFilePath() {
        val stat = MockStat64()
        val ret = Stat.xstat64(null, VERSION, MockCCharPointer("./$file"), stat, MockCIntPointer());
        assertThat(ret).isEqualTo(0)
        assertThat(stat.st_mtim().tv_sec()).isGreaterThanOrEqualTo(writeTime.epochSecond).isLessThan(writeTime.epochSecond + MARGIN_IN_SECONDS)
        assertThat(stat.st_mtim().tv_nsec()).isLessThan(1_000_000_000)
    }

    @Test
    fun modifiedTime() {
        val stat = MockStat64()
        val ret = Stat.xstat64(null, VERSION, MockCCharPointer(file), stat, MockCIntPointer())
        assertThat(ret).isEqualTo(0)
        assertThat(stat.st_mtim().tv_sec()).isGreaterThanOrEqualTo(writeTime.epochSecond).isLessThan(writeTime.epochSecond + MARGIN_IN_SECONDS)
        assertThat(stat.st_mtim().tv_nsec()).isLessThan(1_000_000_000)
    }

    @Test
    fun writingToFileUpdatesModifiedTimestamp() {
        // Wait a few seconds to ensure the modified timestamp is greater than the previous one
        Thread.sleep(WRITE_TIME_GAP_IN_MILLISECONDS)
        val now = Instant.now()
        Files.write(Paths.get(file), data)

        val stat = MockStat64()
        val ret = Stat.xstat64(null, VERSION, MockCCharPointer(file), stat, MockCIntPointer())
        assertThat(ret).isEqualTo(0)
        assertThat(stat.st_mtim().tv_sec()).isGreaterThan(writeTime.epochSecond).isLessThan(now.epochSecond + MARGIN_IN_SECONDS)
        assertThat(stat.st_mtim().tv_nsec()).isLessThan(1_000_000_000)
    }
}