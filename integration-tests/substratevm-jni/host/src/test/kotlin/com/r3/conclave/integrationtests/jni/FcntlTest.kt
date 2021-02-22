package com.r3.conclave.integrationtests.jni

import com.r3.conclave.enclave.internal.substratevm.Fcntl
import com.r3.conclave.integrationtests.jni.tasks.Open
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class FcntlTest : JniTest() {
    companion object {
        const val PATH = "/fcntl.data"
        private const val EXPECTED_FD = 19

        fun openFileForWriting(path: String, oflag: Int): Int {
            val openMessage = Open(path, oflag)
            val fd = sendMessage(openMessage)
            assertThat(fd).isEqualTo(EXPECTED_FD)
            return fd
        }
    }

    @Test
    fun openNonExistingFile() {
        val openMessage = Open("/non-existing-file", Fcntl.O_RDONLY)
        val ret = sendMessage(openMessage)
        assertThat(ret).isEqualTo(-1)
    }

    @Test
    fun openFileForWriting() {
        val fd = openFileForWriting(PATH, Fcntl.O_WRONLY.or(Fcntl.O_CREAT))
        UnistdTest.close(fd)
    }
}