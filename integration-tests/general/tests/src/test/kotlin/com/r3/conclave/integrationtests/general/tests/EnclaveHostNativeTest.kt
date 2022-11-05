package com.r3.conclave.integrationtests.general.tests

import com.r3.conclave.common.EnclaveException
import com.r3.conclave.host.EnclaveHost
import com.r3.conclave.integrationtests.general.common.tasks.*
import com.r3.conclave.integrationtests.general.common.toByteArray
import com.r3.conclave.integrationtests.general.common.toInt
import com.r3.conclave.integrationtests.general.commontest.AbstractEnclaveActionTest
import org.assertj.core.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIfSystemProperty
import java.net.SocketException

class EnclaveHostNativeTest : AbstractEnclaveActionTest() {
    @Test
    fun `simple stateful enclave`() {
        val lookup = mapOf(
            0.toByte() to "Hello",
            1.toByte() to "World",
            2.toByte() to "foo",
            3.toByte() to "bar"
        )

        fun callEnclave(ids: ByteArray): String {
            return callEnclave(StatefulAction(ids)) { id ->
                lookup.getValue(id.single()).toByteArray()
            }
        }

        assertThat(callEnclave(byteArrayOf(2, 1))).isEqualTo("fooWorld")
        assertThat(callEnclave(byteArrayOf(3))).isEqualTo("fooWorldbar")
    }

    // TODO no access to metadata: fun `enclave info`() {}

    @Test
    fun `enclave signing key`() {
        val message = "Hello World".toByteArray()
        val keyAndSignature = callEnclave(SigningAction(message))
        assertThat(enclaveHost().enclaveInstanceInfo.dataSigningKey.encoded).isEqualTo(keyAndSignature.encodedPublicKey)
        enclaveHost().enclaveInstanceInfo.verifier().apply {
            update(message)
            assertThat(verify(keyAndSignature.signature)).isTrue()
        }
    }

    @Test
    fun `several OCALLs`() {
        val ocallResponses = ArrayList<Int>(100)
        callEnclave(RepeatedOcallsAction(100)) { bytes ->
            ocallResponses += bytes.toInt()
            null
        }
        assertThat(ocallResponses).isEqualTo(IntArray(100) { it }.asList())
    }

    @Test
    fun `throwing in ECALL`() {
        assertThatExceptionOfType(RuntimeException::class.java).isThrownBy {
            callEnclave(Thrower())
        }.withMessage(Thrower.CHEERS)
    }

    @Test
    fun `ECALL-OCALL recursion`() {
        var called = 0
        callEnclave(EcallOcallRecursionAction(100)) { bytes ->
            called++
            val response = bytes.toInt() - 1
            response.toByteArray()
        }
        assertThat(called).isEqualTo(50)
    }

    @Test
    fun `get cpu capabilities`() {
        val text = EnclaveHost.getCapabilitiesDiagnostics()
        println(text)
        assertThat(text).contains("SGX available:")
    }

    @Test
    fun `throw if mockEnclave property accessed`() {
        assertThatExceptionOfType(IllegalStateException::class.java).isThrownBy {
            enclaveHost().mockEnclave
        }
    }

    @EnabledIfSystemProperty(named = "runtimeTytpe", matches = "graalvm")
    @Test
    fun `create socket doesn't crash the host in graalvm`() {
        assertThatThrownBy {
            callEnclave(CreateSocket(9999))
        }
            .isInstanceOf(EnclaveException::class.java)
            .hasCauseExactlyInstanceOf(SocketException::class.java)
    }
}
