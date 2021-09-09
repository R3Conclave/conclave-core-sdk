package com.r3.conclave.native_host

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
            val simulationSuffix = if (build == "Simulation") "_sim" else ""
            val prefix = "com/r3/conclave/host-libraries/$build"
            val libraries = mutableListOf(
                "$prefix/libjvm_host.so",
                "$prefix/libjvm_host_shared.so",
                "$prefix/libsgx_enclave_common.so.1",
                "$prefix/libsgx_epid${simulationSuffix}.so.1",
                "$prefix/libsgx_launch${simulationSuffix}.so.1",
                "$prefix/libsgx_uae_service${simulationSuffix}.so",
                "$prefix/libsgx_urts${simulationSuffix}.so",
                "$prefix/libsgx_capable.so",
                "$prefix/libcrypto.so"
            )
            if (simulationSuffix.isEmpty()) {
                libraries.add("$prefix/libprotobuf.so")
            }
            return libraries
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