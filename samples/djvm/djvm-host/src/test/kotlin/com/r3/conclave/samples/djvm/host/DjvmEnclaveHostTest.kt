package com.r3.conclave.samples.djvm.host

import com.r3.conclave.common.OpaqueBytes
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import java.nio.file.Paths

class DjvmEnclaveHostTest {
    companion object {
        private val enclaveHost = DjvmEnclaveHost()

        @BeforeAll
        @JvmStatic
        fun start() {
            val spid = OpaqueBytes.parse(System.getProperty("conclave.samples.spid"))
            val attestationKey = checkNotNull(System.getProperty("conclave.samples.attestation-key"))
            enclaveHost.start(spid, attestationKey)
            enclaveHost.loadJarIntoEnclave(Paths.get(System.getProperty("user-jar.path")))
        }

        @AfterAll
        @JvmStatic
        fun shutdown() {
            enclaveHost.close()
        }
    }

    @Test
    fun testKotlinTask() {
        val response = enclaveHost.runTaskInEnclave("com.r3.conclave.samples.djvm.usercode.KotlinTask", "Hello World")
        assertThat(response).isEqualTo("Sandbox says: 'Hello World'")
    }

    @Test
    fun testBadKotlinTask() {
        val response = enclaveHost.runTaskInEnclave("com.r3.conclave.samples.djvm.usercode.BadKotlinTask", "field")
        assertThat(response).isEqualTo("net.corda.djvm.execution.SandboxException: RuleViolationError: Disallowed reference to API; java.lang.Class.getField(String)")
    }
}
