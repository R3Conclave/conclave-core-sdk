package com.r3.conclave.integrationtests.general.tests.filesystem

import com.r3.conclave.integrationtests.general.common.tasks.RandomAccessFileConcurrentWrites
import com.r3.conclave.integrationtests.general.common.tasks.ReadAndWriteFiles
import com.r3.conclave.integrationtests.general.common.tasks.WriteFilesConcurrently
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class MultiThreadedFileSystemTest : FileSystemEnclaveTest() {
    companion object {
        private const val THREADS = 10
    }

    private fun verifyResponse(reply: List<String>) {
        val expected = (0 until THREADS).map { i ->
            "Dummy text from file $i"
        }
        assertThat(reply).isEqualTo(expected)
    }

    @Test
    fun readWriteManyFiles () {
        val reply = callEnclave(ReadAndWriteFiles(THREADS))
        verifyResponse(reply)
    }

    @Test
    fun multiThreadReadWriteManyFiles () {
        val reply = callEnclave(WriteFilesConcurrently(THREADS))
        verifyResponse(reply)
    }

    @Test
    fun multiThreadReadWriteSingleFile () {
        val replyText = callEnclave(RandomAccessFileConcurrentWrites(THREADS))
        val count = replyText.split("Dummy text from file").size - 1
        assertThat(count).isEqualTo(THREADS)
    }
}
