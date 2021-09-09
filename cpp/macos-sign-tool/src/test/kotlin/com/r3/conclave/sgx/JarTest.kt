package com.r3.conclave.sgx

import com.r3.conclave.internaltesting.jarEntryNames
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.nio.file.Paths

class JarTest {
    companion object {
        private val macosSignToolPath = Paths.get(System.getProperty("macos-sign-tool.path"))

        private fun expectedFiles(): List<String> {
            val prefix = "com/r3/conclave/sign-tool/macos"
            return listOf("$prefix/sgx_sign")
        }
    }

    @Test
    fun jarContainsAllFiles() {
        val expected = expectedFiles()
        assertThat(macosSignToolPath.jarEntryNames()).containsAll(expected)
    }
}