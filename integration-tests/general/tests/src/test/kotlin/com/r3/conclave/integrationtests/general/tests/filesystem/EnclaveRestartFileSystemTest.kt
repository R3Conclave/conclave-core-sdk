package com.r3.conclave.integrationtests.general.tests.filesystem

import com.r3.conclave.host.EnclaveLoadException
import com.r3.conclave.common.EnclaveException
import com.r3.conclave.integrationtests.general.common.tasks.ReadAndWriteFiles
import com.r3.conclave.integrationtests.general.common.tasks.ReadFiles
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

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
            .isInstanceOf(EnclaveException::class.java)
            .hasCauseExactlyInstanceOf(java.nio.file.NoSuchFileException::class.java)
            .cause.hasMessage("/tmp/test_file_0.txt")
    }

    @ParameterizedTest
    @ValueSource(booleans = [false, true])
    fun `can read files after enclave restarts from a restarted persistent filesystem `(useKds: Boolean) {
        this.useKds = useKds

        val reply = restartEnclaveBetweenWriteAndRead("")

        repeat(numThreads) {
            val message = "Dummy text from file $it"
            assertThat(reply).contains(message)
        }
    }

    @Test
    fun `an enclave with persistent disk size bigger than 0 but without host path`() {
        fileSystemFileTempDir = null
        assertThatThrownBy {
            callEnclave(ReadAndWriteFiles(numThreads, ""))
        }
            .isInstanceOf(EnclaveLoadException::class.java)
            .hasMessageContaining("Unable to start enclave")
            .cause.hasMessageContaining("The enclave has been configured to use the persistent filesystem but no storage file was provided in EnclaveHost.start")
    }

    private fun restartEnclaveBetweenWriteAndRead(parentDir: String): String {
        callEnclave(ReadAndWriteFiles(numThreads, parentDir))
        restartEnclave()
        val texts = callEnclave(ReadFiles(numThreads, parentDir))
        return texts.joinToString()
    }
}
