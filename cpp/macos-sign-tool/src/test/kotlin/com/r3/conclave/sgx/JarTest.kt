package com.r3.conclave.sgx

import org.assertj.core.api.Assertions
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Paths
import java.util.jar.JarInputStream

class JarTest {
    companion object {
        private val macosSignToolPath = Paths.get(System.getProperty("macos-sign-tool.path"))

        private fun expectedFiles(): List<String> {
            val prefix = "com/r3/conclave/sign-tool/macos"
            return listOf("$prefix/sgx_sign")
        }

        private fun getAllJarEntries(jarInputStream: JarInputStream): List<String> {
            val entries = mutableListOf<String>()
            var entry = jarInputStream.nextEntry
            while (entry != null) {
                entries.add(entry.name)
                entry = jarInputStream.nextEntry
            }
            return entries
        }
    }

    @Test
    fun jarContainsAllFiles() {
        val expected = expectedFiles()
        Files.newInputStream(macosSignToolPath).use { input ->
            val jarInputStream = JarInputStream(input)
            val result = getAllJarEntries(jarInputStream)
            Assertions.assertThat(result).containsAll(expected)
        }
    }
}