package com.r3.conclave.integrationtests.jni

import com.r3.conclave.enclave.internal.substratevm.ErrnoBase
import com.r3.conclave.enclave.internal.substratevm.Fcntl
import com.r3.conclave.integrationtests.jni.tasks.Time
import com.r3.conclave.integrationtests.jni.tasks.XStat64
import com.r3.conclave.jvm.enclave.common.internal.testing.MockStat64Data
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

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
        val enclaveCurrentTimeInSeconds = sendMessage(Time())
        val fd = FcntlTest.openFileForWriting(path, Fcntl.O_WRONLY.or(Fcntl.O_CREAT))
        val xstat64Message = XStat64(VERSION, path, MockStat64Data())
        var response = sendMessage(xstat64Message)
        assertThat(response.ret).isZero
        assertThat(response.buf.timespec.sec).isGreaterThanOrEqualTo(enclaveCurrentTimeInSeconds)
        assertThat(response.buf.timespec.nsec).isLessThan(1_000_000_000)

        Thread.sleep(WRITE_TIME_GAP_IN_MILLISECONDS)
        val data = byteArrayOf(1, 2, 3, 4)
        val writeTimeInSeconds = sendMessage(Time())
        UnistdTest.write(fd, data, data.size)
        response = sendMessage(xstat64Message)
        assertThat(response.ret).isZero
        assertThat(response.buf.timespec.sec)
                .isGreaterThanOrEqualTo(writeTimeInSeconds)
                .isGreaterThan(enclaveCurrentTimeInSeconds)
        assertThat(response.buf.timespec.nsec).isLessThan(1_000_000_000)
        UnistdTest.close(fd)
    }
}