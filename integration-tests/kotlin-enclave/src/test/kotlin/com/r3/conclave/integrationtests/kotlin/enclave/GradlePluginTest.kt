package com.r3.conclave.integrationtests.kotlin.enclave

import com.r3.conclave.host.EnclaveHost
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test

class GradlePluginTest {
    private lateinit var host: EnclaveHost

    @AfterEach
    fun cleanUp() {
        if (::host.isInitialized) {
            host.close()
        }
    }

    @Test
    fun `enclave properties specified in the build file are wired up to EnclaveInfo`() {
        host = EnclaveHost.load("com.r3.conclave.integrationtests.kotlin.enclave.KotlinEnclave")
        host.start(null, null, null, null)
        val enclaveInfo = host.enclaveInstanceInfo.enclaveInfo
        assertThat(enclaveInfo.productID).isEqualTo(100)
        assertThat(enclaveInfo.revocationLevel).isEqualTo(2)
    }
}
