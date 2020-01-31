package com.r3.conclave.sample.host

import com.r3.conclave.host.EnclaveMode
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class HostTest {
    @Test
    fun `run host`() {
        val response = callEnclave("123456".toByteArray(), EnclaveMode.SIMULATION)
        assertThat(response).isEqualTo("654321".toByteArray())
    }
}
