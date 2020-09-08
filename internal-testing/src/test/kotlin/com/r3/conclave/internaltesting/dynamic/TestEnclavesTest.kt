package com.r3.conclave.internaltesting.dynamic

import com.r3.conclave.enclave.Enclave
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import java.nio.file.Files

class TestEnclavesTest {
    companion object {
        @JvmField
        @RegisterExtension
        val testEnclaves = TestEnclaves()
    }

    @Test
    fun `test enclave classes from the same location produce different jar files`() {
        val jarA = testEnclaves.getEnclaveJar(EnclaveA::class.java).toPath()
        val jarB = testEnclaves.getEnclaveJar(EnclaveB::class.java).toPath()
        assertThat(Files.readAllBytes(jarA)).isNotEqualTo(Files.readAllBytes(jarB))
    }

    @Test
    fun `caching works`() {
        val jar1 = testEnclaves.getEnclaveJar(EnclaveA::class.java).toPath()
        val jar2 = testEnclaves.getEnclaveJar(EnclaveA::class.java).toPath()
        assertThat(jar1).isEqualTo(jar2)
    }

    class EnclaveA : Enclave()
    class EnclaveB : Enclave()
}
