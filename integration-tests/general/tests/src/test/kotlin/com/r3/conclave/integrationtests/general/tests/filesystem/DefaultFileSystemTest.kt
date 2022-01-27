package com.r3.conclave.integrationtests.general.tests.filesystem

import com.r3.conclave.integrationtests.general.common.tasks.ReadAndWriteFilesToDefaultFileSystem
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class DefaultFileSystemTest : FileSystemEnclaveTest() {
    private val fileNumber = 5

    @Test
    fun `read and write files to default filesystem`() {
        val texts = callEnclave(ReadAndWriteFilesToDefaultFileSystem(fileNumber))
        assertThat(texts).isEqualTo((0 until fileNumber).map { "Dummy text from file $it" })
    }
}
