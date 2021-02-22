package com.r3.conclave.integrationtests.jni

import com.r3.conclave.enclave.internal.substratevm.ErrnoBase
import com.r3.conclave.enclave.internal.substratevm.Fcntl
import com.r3.conclave.integrationtests.jni.tasks.XStat64
import com.r3.conclave.jvm.enclave.common.internal.testing.MockStat64Data
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.Instant

class StatTest : JniTest() {

    companion object {
        private const val VERSION = 0
        private const val WRITE_TIME_GAP_IN_MILLISECONDS = 2000L
    }

    @Test
    fun xstat64NonExistingFile() {
        val path = "/non-existing-file"
        val xstat64Message = XStat64(VERSION, path, MockStat64Data())
        val response = sendMessage(xstat64Message)
        assertThat(response.ret).isEqualTo(-1)
        assertThat(response.errno).isEqualTo(ErrnoBase.ENOENT)
    }

    @Test
    fun xstat64EmptyPath() {
        val path = ""
        val xstat64Message = XStat64(VERSION, path, MockStat64Data())
        val response = sendMessage(xstat64Message)
        assertThat(response.ret).isEqualTo(-1)
        assertThat(response.errno).isEqualTo(ErrnoBase.ENOENT)
    }

    @Test
    fun modifiedTime() {
        val path = "/stat.data"
        val openTime = Instant.now()
        val fd = FcntlTest.openFileForWriting(path, Fcntl.O_WRONLY.or(Fcntl.O_CREAT))
        val xstat64Message = XStat64(VERSION, path, MockStat64Data())
        var response = sendMessage(xstat64Message)
        assertThat(response.ret).isZero
        assertThat(response.buf.timespec.sec).isGreaterThanOrEqualTo(openTime.epochSecond)
        assertThat(response.buf.timespec.nsec).isLessThan(1_000_000_000)

        Thread.sleep(WRITE_TIME_GAP_IN_MILLISECONDS)
        val data = byteArrayOf(1, 2, 3, 4)
        val writeTime = Instant.now()
        UnistdTest.write(fd, data, data.size)
        response = sendMessage(xstat64Message)
        assertThat(response.ret).isZero
        assertThat(response.buf.timespec.sec)
                .isGreaterThanOrEqualTo(writeTime.epochSecond)
                .isGreaterThan(openTime.epochSecond)
        assertThat(response.buf.timespec.nsec).isLessThan(1_000_000_000)
        UnistdTest.close(fd)
    }
}