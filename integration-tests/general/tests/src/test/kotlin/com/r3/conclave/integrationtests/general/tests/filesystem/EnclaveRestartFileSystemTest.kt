package com.r3.conclave.integrationtests.general.tests.filesystem

import com.r3.conclave.integrationtests.general.common.tasks.ReadAndWriteFiles
import com.r3.conclave.integrationtests.general.common.tasks.ReadFiles
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

class EnclaveRestartFileSystemTest : FileSystemEnclaveTest() {
    private val numThreads: Int = 5

    @Test
    fun `cannot read previously saved files after enclave restarts when using in-memory filesystem`() {
        //  Files that have been written in the in-memory filesystems are not
        //    present anymore after a restart.
        //  Files like /tmp/test_file_0.txt are written in ReadAndWriteFiles, but then they are not found again
        //    when calling ReadFiles after the restart of the Enclave
        assertThatThrownBy {
            restartEnclaveBetweenWriteAndRead("/tmp")
        }
            .isInstanceOf(RuntimeException::class.java)
            .hasMessageContaining("java.nio.file.NoSuchFileException: /tmp/test_file_0.txt")
    }

    @Test
    fun `can read files after enclave restarts from a restarted persistent filesystem `() {
        val reply = restartEnclaveBetweenWriteAndRead("")

        repeat(numThreads) { it ->
            val message = "Dummy text from file $it"
            assertThat(reply).contains(message)
        }
    }

    @Test
    fun `an enclave with persistent disk size bigger than 0 but without host path crashes`() {
        //  This will override the JUnit assigned member variable in AbstractEnclaveActionTest class
        //      as we want to call the EnclaveHost.load function without specifying the filesystem file path
        fileSystemFileTempDir = null
        assertThatThrownBy {
            callEnclave(ReadAndWriteFiles(numThreads, ""))
        }
            .isInstanceOf(com.r3.conclave.host.EnclaveLoadException::class.java)
            .hasMessageContaining("Unable to start enclave")
            .hasStackTraceContaining("Caused by: java.lang.RuntimeException: Filesystems not initialized, path not provided for drive")
    }

    private fun restartEnclaveBetweenWriteAndRead(parentDir: String): String {
        callEnclave(ReadAndWriteFiles(numThreads, parentDir))
        restartEnclave()
        val texts = callEnclave(ReadFiles(numThreads, parentDir))
        return texts.joinToString()
    }
}
