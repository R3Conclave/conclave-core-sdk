package com.r3.conclave.integrationtests.general.tests.filesystem

import com.r3.conclave.integrationtests.general.common.tasks.ReadAndWriteFilesToDefaultFileSystem
import com.r3.conclave.integrationtests.general.commontest.TestUtils.graalvmOnlyTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class DefaultFileSystemTest : FileSystemEnclaveTest() {
    private val fileNumber = 5

    @Test
    fun `read and write files to default filesystem`() {
        graalvmOnlyTest() // CON-1264: Gramine: accessing filesystem and devices causes InvalidKeyException: Invalid AES key length: 0 bytes
        val texts = callEnclave(ReadAndWriteFilesToDefaultFileSystem(fileNumber))
        assertThat(texts).isEqualTo((0 until fileNumber).map { "Dummy text from file $it" })
    }
}
