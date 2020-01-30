package com.r3.conclave.sample.host

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.nio.file.Paths

class HostTest {
    companion object {
        private val enclaveFile = Paths.get(System.getProperty("com.r3.conclave.sample.enclavepath"))
    }

    @Test
    fun `run host`() {
        val response = callEnclave(enclaveFile, "123456".toByteArray())
        assertThat(response).isEqualTo("654321".toByteArray())
    }
}
