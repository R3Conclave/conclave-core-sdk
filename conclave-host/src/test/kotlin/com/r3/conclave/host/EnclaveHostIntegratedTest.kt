package com.r3.conclave.host

import com.r3.conclave.common.enclave.EnclaveCall
import com.r3.conclave.enclave.Enclave
import com.r3.sgx.core.host.EnclaveLoadMode
import com.r3.sgx.dynamictesting.TestEnclaves
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test

class EnclaveHostIntegratedTest {
    companion object {
        private val testEnclaves = TestEnclaves()

        @BeforeAll
        @JvmStatic
        fun before() {
            testEnclaves.before()
        }

        @AfterAll
        @JvmStatic
        fun after() {
            testEnclaves.after()
        }
    }

    @Test
    fun `simple stateful enclave`() {
        val lookup = mapOf(
                0.toByte() to "Hello",
                1.toByte() to "World",
                2.toByte() to "foo",
                3.toByte() to "bar"
        )
        val host = hostTo<StatefulEnclave>()
        host.start()

        fun callEnclave(ids: ByteArray): String? {
            val response = host.callEnclave(ids) { id ->
                lookup.getValue(id.single()).toByteArray()
            }
            return response?.let { String(it) }
        }

        assertThat(callEnclave(byteArrayOf(2, 1))).isEqualTo("fooWorld")
        assertThat(callEnclave(byteArrayOf(3))).isEqualTo("fooWorldbar")

        host.close()
    }

    private inline fun <reified T : Enclave> hostTo(): EnclaveHost {
        val enclaveFile = testEnclaves.getEnclave(T::class.java)
        return EnclaveHost.create(enclaveFile.toPath(), EnclaveLoadMode.SIMULATION)
    }

    class StatefulEnclave : EnclaveCall, Enclave() {
        private var previousResult = ""

        override fun invoke(bytes: ByteArray): ByteArray? {
            val builder = StringBuilder(previousResult)
            for (byte in bytes) {
                val lookupValue = callUntrustedHost(byteArrayOf(byte))!!
                builder.append(String(lookupValue))
            }
            val result = builder.toString()
            previousResult = result
            return result.toByteArray()
        }
    }
}
