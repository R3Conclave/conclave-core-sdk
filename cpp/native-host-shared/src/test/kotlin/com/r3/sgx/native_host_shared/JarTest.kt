package com.r3.sgx.native_host

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.nio.file.Paths
import java.nio.file.Files
import java.util.jar.JarInputStream

class JarTest {

    companion object {
        private val jarPath = Paths.get(System.getProperty("jar.path")
                ?: throw AssertionError("System property 'jar.path' not set."))

        private fun expectedLibraries(): List<String> {
            val prefix = "com/r3/sgx/host-libraries/shared"
            val libraries = mutableListOf(
                    "$prefix/libsgx_capable.so"
            )
            return libraries
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
    fun jarContainsAllLibraries() {
        val expected = expectedLibraries()
        Files.newInputStream(jarPath).use { input ->
            val jarInputStream = JarInputStream(input)
            val result = getAllJarEntries(jarInputStream)
            assertThat(result).containsAll(expected)
        }
    }
}