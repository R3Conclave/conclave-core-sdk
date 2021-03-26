package com.r3.conclave.integrationtests.general.tests

import com.r3.conclave.host.EnclaveHost
import com.r3.conclave.integrationtests.general.common.tasks.*
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatExceptionOfType
import org.junit.jupiter.api.Test
import java.util.stream.IntStream
import kotlin.streams.toList

class EnclaveHostNativeTest : JvmTest(threadSafe = false) {
    @Test
    fun `simple stateful enclave`() {
        val lookup = mapOf(
            0.toByte() to "Hello",
            1.toByte() to "World",
            2.toByte() to "foo",
            3.toByte() to "bar"
        )

        fun callEnclave(ids: ByteArray): String {
            val response = sendMessage(WithState(ids)){
                id -> lookup.getValue(id.single()).toByteArray()
            }
            return String(response)
        }

        assertThat(callEnclave(byteArrayOf(2, 1))).isEqualTo("fooWorld")
        assertThat(callEnclave(byteArrayOf(3))).isEqualTo("fooWorldbar")
    }

    // TODO no access to metadata: fun `enclave info`() {}

    @Test
    fun `enclave signing key`() {
        val message = "Hello World".toByteArray()
        val signer = Signer(message)
        val dis = sendMessage(signer).dataStream()
        val signingKeyBytes = dis.readIntLengthPrefixBytes()
        val signature = dis.readIntLengthPrefixBytes()
        assertThat(enclaveHost.enclaveInstanceInfo.dataSigningKey.encoded).isEqualTo(signingKeyBytes)
        enclaveHost.enclaveInstanceInfo.verifier().apply {
            update(message)
            assertThat(verify(signature)).isTrue()
        }
    }

    @Test
    fun `several OCALLs`() {
        val repeater = RepeatedOcall(100)
        val ocallResponses = RecordingCallback()
        sendMessage(repeater, ocallResponses)
        assertThat(ocallResponses.calls.map { it.toInt() }).isEqualTo(IntArray(100) { it }.asList())
    }

    @Test
    fun `concurrent calls into the enclave`() {
        val n = 10000
        sendMessage(SetInt(Adder.maxCallCountKey, n))

        val sum = IntStream.rangeClosed(1, n)
            .parallel()
            .mapToObj { sendMessage(Adder(it)) }
            .toList().single { it > 0 }
        assertThat(sum).isEqualTo((n * (n + 1)) / 2)
    }

    @Test
    fun `concurrent calls into the enclave with call backs`() {
        val n = 100
        val sums = IntStream.rangeClosed(1, n)
            .parallel()
            .map { i ->
                var sum = 0
                sendMessage(RepeatedOcall(i)) {
                    sum += it.toInt() + 1
                    null
                }
                sum
            }
            .toList()
        assertThat(sums).isEqualTo((1..n).map { (it * (it + 1)) / 2 })
    }


    @Test
    fun `throwing in ECALL`() {
        val op = Thrower()
        assertThatExceptionOfType(RuntimeException::class.java).isThrownBy {
            sendMessage(op)
        }.withMessage(Thrower.CHEERS)
    }

    @Test
    fun `ECALL-OCALL recursion`() {
        val callback = object : (ByteArray) -> ByteArray? {
            var called = 0
            override fun invoke(bytes: ByteArray): ByteArray {
                called++
                val response = bytes.toInt() - 1
                return response.toByteArray()
            }
        }
        sendMessage(Recursing(100), callback)
        assertThat(callback.called).isEqualTo(50)
    }

    @Test
    fun `get cpu capabilities`() {
        val text = EnclaveHost.getCapabilitiesDiagnostics()
        println(text)
        assertThat(text).contains("SGX available:")
    }
}
