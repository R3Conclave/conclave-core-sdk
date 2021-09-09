package com.r3.conclave.substratevm

import com.r3.conclave.internaltesting.jarEntryNames
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.nio.file.Paths

class JarTest {
    companion object {
        private val simulationJarPath = Paths.get(System.getProperty("simulation-jar.path")!!)
        private val debugJarPath = Paths.get(System.getProperty("debug-jar.path")!!)
        private val releaseJarPath = Paths.get(System.getProperty("release-jar.path")!!)

        private fun expectedLibraries(build: String): List<String> {
            val prefix = "com/r3/conclave/substratevm/$build"
            return mutableListOf(
                "$prefix/libsubstratevm.a",
                "$prefix/libjvm_host_enclave_common_enclave.a",
                "$prefix/libjvm_enclave_edl.a",
                "$prefix/libjvm_enclave_common.a",
                "$prefix/libz.a"
            )
        }
    }

    @Test
    fun simulationJarContainsAllLibraries() {
        val expected = expectedLibraries("Simulation")
        assertThat(simulationJarPath.jarEntryNames()).containsAll(expected)
    }

    @Test
    fun debugJarContainsAllLibraries() {
        val expected = expectedLibraries("Debug")
        assertThat(debugJarPath.jarEntryNames()).containsAll(expected)
    }

    @Test
    fun releaseJarContainsAllLibraries() {
        val expected = expectedLibraries("Release")
        assertThat(releaseJarPath.jarEntryNames()).containsAll(expected)
    }
}