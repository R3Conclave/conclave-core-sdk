package com.r3.conclave.integrationtests.general.tests.filesystem

import com.r3.conclave.integrationtests.general.common.tasks.RandomAccessFileConcurrentWrites
import com.r3.conclave.integrationtests.general.common.tasks.ReadAndWriteFiles
import com.r3.conclave.integrationtests.general.common.tasks.WriteFilesConcurrently
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

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

    @ParameterizedTest
    @ValueSource(strings = ["", "/tmp"])
    fun readWriteManyFiles(path: String) {
        val reply = callEnclave(ReadAndWriteFiles(THREADS, path))
        verifyResponse(reply)
    }

    @ParameterizedTest
    @ValueSource(strings = ["", "/tmp"])
    fun multiThreadReadWriteManyFiles(path: String) {
        val reply = callEnclave(WriteFilesConcurrently(THREADS, path))
        verifyResponse(reply)
    }

    @ParameterizedTest
    @ValueSource(strings = ["", "/tmp"])
    fun multiThreadReadWriteSingleFile(path: String) {
        val replyText = callEnclave(RandomAccessFileConcurrentWrites(THREADS, path))
        val count = replyText.split("Dummy text from file").size - 1
        assertThat(count).isEqualTo(THREADS)
    }
}
