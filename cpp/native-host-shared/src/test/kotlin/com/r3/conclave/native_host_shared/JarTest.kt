package com.r3.conclave.native_host_shared

import com.r3.conclave.internaltesting.jarEntryNames
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.nio.file.Paths

class JarTest {
    companion object {
        private val jarPath = Paths.get(System.getProperty("jar.path")!!)

        private fun expectedLibraries(): List<String> {
            val prefix = "com/r3/conclave/host-libraries/shared"
            return mutableListOf(
                "$prefix/libsgx_capable.so"
            )
        }
    }

    @Test
    fun jarContainsAllLibraries() {
        val expected = expectedLibraries()
        assertThat(jarPath.jarEntryNames()).containsAll(expected)
    }
}