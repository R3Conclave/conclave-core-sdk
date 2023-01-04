package com.r3.conclave.integrationtests.general.tests.filesystem

import com.r3.conclave.integrationtests.general.common.tasks.RandomAccessFileConcurrentWrites
import com.r3.conclave.integrationtests.general.common.tasks.ReadAndWriteFiles
import com.r3.conclave.integrationtests.general.common.tasks.WriteFilesConcurrently
import com.r3.conclave.integrationtests.general.commontest.TestUtils.graalvmOnlyTest
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
        graalvmOnlyTest() // CON-1264: Gramine: accessing filesystem and devices causes InvalidKeyException: Invalid AES key length: 0 bytes
        val reply = callEnclave(ReadAndWriteFiles(THREADS, path))
        verifyResponse(reply)
    }

    @ParameterizedTest
    @ValueSource(strings = [""])
    // Testing the in-memory filesystem has been temporarily disabled as it is flaky
    // TODO: CON-1281 - Fix multithreading issue and readd the test for "/tmp"
    fun multiThreadReadWriteManyFiles(path: String) {
        graalvmOnlyTest() // CON-1264: Gramine: accessing filesystem and devices causes InvalidKeyException: Invalid AES key length: 0 bytes
        val reply = callEnclave(WriteFilesConcurrently(THREADS, path))
        verifyResponse(reply)
    }

    @ParameterizedTest
    @ValueSource(strings = ["", "/tmp"])
    fun multiThreadReadWriteSingleFile(path: String) {
        graalvmOnlyTest() // CON-1264: Gramine: accessing filesystem and devices causes InvalidKeyException: Invalid AES key length: 0 bytes
        val replyText = callEnclave(RandomAccessFileConcurrentWrites(THREADS, path))
        val count = replyText.split("Dummy text from file").size - 1
        assertThat(count).isEqualTo(THREADS)
    }
}
