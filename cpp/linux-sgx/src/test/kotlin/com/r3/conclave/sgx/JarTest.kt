package com.r3.conclave.sgx

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Paths
import java.util.jar.JarInputStream

class JarTest {

    companion object {
        private val simulationJarPath = Paths.get(
            System.getProperty("simulation-jar.path")
                ?: throw AssertionError("System property 'simulation-jar.path' not set.")
        )

        private val debugJarPath = Paths.get(
            System.getProperty("debug-jar.path")
                ?: throw AssertionError("System property 'debug-jar.path' not set.")
        )

        private val releaseJarPath = Paths.get(
            System.getProperty("release-jar.path")
                ?: throw AssertionError("System property 'release-jar.path' not set.")
        )

        private fun expectedLibraries(build: String): List<String> {
            val prefix = "com/r3/conclave/sgx/$build"
            val simSuffix = if (build == "Simulation") "_sim" else ""
            return mutableListOf(
                "$prefix/tlibc/",
                "$prefix/libcxx/",
                "$prefix/libsgx_pthread.a",
                "$prefix/libsgx_tcrypto.a",
                "$prefix/libsgx_tcxx.a",
                "$prefix/libsgx_trts${simSuffix}.a",
                "$prefix/libsgx_tservice${simSuffix}.a",
                "$prefix/libsgx_tstdc.a"
            )
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
    fun simulationJarContainsAllLibraries() {
        val expected = expectedLibraries("Simulation")
        Files.newInputStream(simulationJarPath).use { input ->
            val jarInputStream = JarInputStream(input)
            val result = getAllJarEntries(jarInputStream)
            assertThat(result).containsAll(expected)
        }
    }

    @Test
    fun debugJarContainsAllLibraries() {
        val expected = expectedLibraries("Debug")
        Files.newInputStream(debugJarPath).use { input ->
            val jarInputStream = JarInputStream(input)
            val result = getAllJarEntries(jarInputStream)
            assertThat(result).containsAll(expected)
        }
    }

    @Test
    fun releaseJarContainsAllLibraries() {
        val expected = expectedLibraries("Release")
        Files.newInputStream(releaseJarPath).use { input ->
            val jarInputStream = JarInputStream(input)
            val result = getAllJarEntries(jarInputStream)
            assertThat(result).containsAll(expected)
        }
    }
}