package com.r3.conclave.enclave.internal.substratevm

import com.r3.conclave.internaltesting.jarEntryNames
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.nio.file.Paths

class PosixDirectivesTest {
    companion object {
        private val conclaveEnclaveJarPath = Paths.get(System.getProperty("jvmEnclaveCommonJarPath")!!)
    }

    @Test
    fun containsAllHeaderFiles() {
        val headers = conclaveEnclaveJarPath.jarEntryNames()
            .filter { it.endsWith(".h") }
            .map { it.removePrefix("com/r3/conclave/include/") }
        val expectedHeaderFiles = PosixDirectives().headerFiles.map { it.replace("\"", "") }.toTypedArray()
        assertThat(headers).containsExactlyInAnyOrder(*expectedHeaderFiles)
    }
}
