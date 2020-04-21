package com.r3.sgx.native_host

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.nio.file.Paths
import java.util.jar.JarInputStream

class JarTest {

    companion object {
        private val simulationJarPath = Paths.get(System.getProperty("simulation-jar.path")
                ?: throw AssertionError("System property 'simulation-jar.path' not set."))

        private val debugJarPath = Paths.get(System.getProperty("debug-jar.path")
                ?: throw AssertionError("System property 'debug-jar.path' not set."))

        private val releaseJarPath = Paths.get(System.getProperty("release-jar.path")
                ?: throw AssertionError("System property 'release-jar.path' not set."))

        private fun expectedLibraries(build: String): List<String> {
            val simulationSuffix = if (build == "Simulation") "_sim" else ""
            val prefix = "com/r3/sgx/host-libraries/$build"
            val libraries = mutableListOf(
                    "$prefix/libjvm_host.so",
                    "$prefix/libsgx_enclave_common.so.1",
                    "$prefix/libsgx_epid${simulationSuffix}.so.1",
                    "$prefix/libsgx_launch${simulationSuffix}.so.1",
                    "$prefix/libsgx_uae_service${simulationSuffix}.so",
                    "$prefix/libsgx_urts${simulationSuffix}.so",
                    "$prefix/libcrypto.so.1.0.0"
            )
            if (simulationSuffix.isEmpty()) {
                libraries.add("$prefix/libprotobuf.so.10")
            }
            return libraries
        }

        private fun getAllJarEntries(jarInputStream: JarInputStream) : List<String> {
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
    fun simulationJarContainsAllLibraries() {
        val expected = expectedLibraries("Simulation")
        val jarInputStream = JarInputStream(simulationJarPath.toFile().inputStream())
        val result = getAllJarEntries(jarInputStream)
        assertThat(result).containsAll(expected)
    }

    @Test
    fun debugJarContainsAllLibraries() {
        val expected = expectedLibraries("Debug")
        val jarInputStream = JarInputStream(debugJarPath.toFile().inputStream())
        val result = getAllJarEntries(jarInputStream)
        assertThat(result).containsAll(expected)

    }

    @Test
    fun releaseJarContainsAllLibraries() {
        val expected = expectedLibraries("Release")
        val jarInputStream = JarInputStream(releaseJarPath.toFile().inputStream())
        val result = getAllJarEntries(jarInputStream)
        assertThat(result).containsAll(expected)
    }
}