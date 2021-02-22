package com.r3.conclave.enclave.internal.substratevm

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Paths
import java.util.jar.JarInputStream

class PosixDirectivesTest {
    companion object {
        private val conclaveEnclaveJarPath = Paths.get(System.getProperty("jvmEnclaveCommonJarPath")
                ?: throw AssertionError("System property 'jvmEnclaveCommonJarPath' not set."))

        private fun getHeaders(jarInputStream: JarInputStream): List<String> {
            val entries = mutableListOf<String>()
            var entry = jarInputStream.nextEntry
            while (entry != null) {
                if (entry.name.endsWith(".h")) {
                    entries.add(entry.name)
                }
                entry = jarInputStream.nextEntry
            }
            return entries
        }
    }

    @Test
    fun containsAllHeaderFiles() {
        val expectedHeaderFiles = PosixDirectives().headerFiles.map { it.replace("\"", "") }.toTypedArray()
        Files.newInputStream(conclaveEnclaveJarPath).use { inputStream ->
            JarInputStream(inputStream).use { jarInputStream ->
                val headers = getHeaders(jarInputStream).map { it.removePrefix("com/r3/conclave/include/") }
                assertThat(headers).containsExactlyInAnyOrder(*expectedHeaderFiles)
            }
        }
    }
}