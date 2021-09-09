package com.r3.conclave.sgx

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