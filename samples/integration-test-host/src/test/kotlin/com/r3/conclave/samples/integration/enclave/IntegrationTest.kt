package com.r3.conclave.samples.integration.enclave

import com.r3.conclave.common.OpaqueBytes
import com.r3.conclave.samples.integration.host.IntegrationTestHost
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test

/**
 * The ideal integration test is the hello world sample and we should leverage it's test where possible. However, as it's
 * a tutorial in our public docs, it may not be wise to convolute it in ways which may be necessary for testing specific
 * scenerios but would complicate it as a sample. For example, specifying a non-zero revocation level.
 */
class IntegrationTest {
    companion object {
        private val enclaveHost = IntegrationTestHost()

        @BeforeAll
        @JvmStatic
        fun start() {
            val spid = OpaqueBytes.parse(System.getProperty("conclave.samples.spid"))
            val attestationKey = checkNotNull(System.getProperty("conclave.samples.attestation-key"))
            enclaveHost.start(spid, attestationKey)
        }

        @AfterAll
        @JvmStatic
        fun shutdown() {
            enclaveHost.close()
        }
    }

    @Test
    fun `enclave properties specified in the build file are wired up to EnclaveInfo`() {
        val enclaveInfo = enclaveHost.enclaveInstanceInfo.enclaveInfo
        assertThat(enclaveInfo.productID).isEqualTo(100)
        assertThat(enclaveInfo.revocationLevel).isEqualTo(2)
    }
}
