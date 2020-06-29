package com.r3.conclave.host

import com.r3.conclave.common.SHA256Hash
import com.r3.conclave.common.SecureHash
import com.r3.conclave.common.enclave.EnclaveCall
import com.r3.conclave.common.internal.dataStream
import com.r3.conclave.common.internal.readIntLengthPrefixBytes
import com.r3.conclave.common.internal.writeData
import com.r3.conclave.common.internal.writeIntLengthPrefixBytes
import com.r3.conclave.dynamictesting.EnclaveBuilder
import com.r3.conclave.dynamictesting.EnclaveConfig
import com.r3.conclave.dynamictesting.TestEnclaves
import com.r3.conclave.enclave.Enclave
import com.r3.conclave.host.kotlin.callEnclave
import com.r3.conclave.testing.RecordingEnclaveCall
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatExceptionOfType
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import java.nio.ByteBuffer
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicInteger
import java.util.stream.IntStream
import kotlin.streams.asSequence
import kotlin.streams.toList

class EnclaveHostNativeTest {
    companion object {
        @JvmField
        @RegisterExtension
        val testEnclaves = TestEnclaves()
    }

    private lateinit var host: EnclaveHost

    @AfterEach
    fun cleanUp() {
        if (::host.isInitialized) {
            host.close()
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
        start<StatefulEnclave>()

        fun callEnclave(ids: ByteArray): String? {
            val response = host.callEnclave(ids) { id ->
                lookup.getValue(id.single()).toByteArray()
            }
            return response?.let { String(it) }
        }

        assertThat(callEnclave(byteArrayOf(2, 1))).isEqualTo("fooWorld")
        assertThat(callEnclave(byteArrayOf(3))).isEqualTo("fooWorldbar")
    }

    @Test
    fun `enclave info`() {
        start<StatefulEnclave>()
        val metadataFile = testEnclaves.getEnclaveMetadata(StatefulEnclave::class.java, EnclaveBuilder())
        host.enclaveInstanceInfo.enclaveInfo.apply {
            assertThat(codeHash).isEqualTo(getMeasurement(metadataFile))
            assertThat(codeSigningKeyHash).isEqualTo(getMrsigner(metadataFile))
        }
    }

    @Test
    fun `enclave signing key`() {
        start<SigningEnclave>()
        val message = "Hello World".toByteArray()
        val dis = host.callEnclave(message)!!.dataStream()
        val signingKeyBytes = dis.readIntLengthPrefixBytes()
        val signature = dis.readIntLengthPrefixBytes()
        assertThat(host.enclaveInstanceInfo.dataSigningKey.encoded).isEqualTo(signingKeyBytes)
        host.enclaveInstanceInfo.verifier().apply {
            update(message)
            assertThat(verify(signature)).isTrue()
        }
    }

    @Test
    fun `several OCALLs`() {
        start<RepeatedOcallEnclave>()
        val ocallResponses = RecordingEnclaveCall()
        host.callEnclave(100.toByteArray(), ocallResponses)
        assertThat(ocallResponses.calls.map { it.toInt() }).isEqualTo(IntArray(100) { it }.asList())
    }

    @Test
    fun `concurrent calls into the enclave`() {
        val n = 10000
        start<AddingEnclave>(EnclaveBuilder(config = EnclaveConfig().withTCSNum(20)))
        host.callEnclave(n.toByteArray())
        val sum = IntStream.rangeClosed(1, n)
                .parallel()
                .mapToObj { host.callEnclave(it.toByteArray())?.toInt() }
                .toList()
                .mapNotNull { it }
                .single()
        assertThat(sum).isEqualTo((n * (n + 1)) / 2)
    }

    @Test
    fun `concurrent calls into the enclave with call backs`() {
        val n = 100
        start<RepeatedOcallEnclave>(EnclaveBuilder(config = EnclaveConfig().withTCSNum(20)))
        val sums = IntStream.rangeClosed(1, n)
                .parallel()
                .map { i ->
                    var sum = 0
                    host.callEnclave(i.toByteArray()) {
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
        start<ThrowingEnclave>()
        assertThatExceptionOfType(RuntimeException::class.java).isThrownBy {
            host.callEnclave(byteArrayOf())
        }.withMessage(ThrowingEnclave.CHEERS)
    }

    @Test
    fun `ECALL-OCALL recursion`() {
        start<RecursingEnclave>()
        val callback = object : EnclaveCall {
            var called = 0
            override fun invoke(bytes: ByteArray): ByteArray? {
                called++
                val response = bytes.toInt() - 1
                return response.toByteArray()
            }
        }
        host.callEnclave(100.toByteArray(), callback)
        assertThat(callback.called).isEqualTo(50)
    }

    private inline fun <reified T : Enclave> start(enclaveBuilder: EnclaveBuilder = EnclaveBuilder()) {
        host = testEnclaves.hostTo<T>(enclaveBuilder).apply {
            start(null, null)
        }
    }

    private fun getSha256Value(metadataFile: Path, key: String): SecureHash {
        // Example:
        //
        // metadata->enclave_css.body.enclave_hash.m:
        // 0xca 0x5d 0xb9 0x8c 0xde 0x0d 0x87 0xe5 0x47 0x0b 0x16 0x89 0x79 0xa2 0xa2 0x63
        // 0xe3 0xc9 0x99 0x19 0x61 0x63 0xf3 0xb5 0xda 0x3e 0x46 0xa8 0xa4 0x97 0xad 0x0d
        return Files.lines(metadataFile).use { lines ->
            lines.asSequence()
                    .dropWhile { line -> line != key }
                    .drop(1)
                    .take(2)
                    .flatMap { line -> line.splitToSequence(' ').map { it.removePrefix("0x") } }
                    .joinToString("")
                    .let { SHA256Hash.parse(it) }
        }
    }

    private fun getMeasurement(metadataFile: Path): SecureHash {
        return getSha256Value(metadataFile, "metadata->enclave_css.body.enclave_hash.m:")
    }

    private fun getMrsigner(metadataFile: Path): SecureHash {
        return getSha256Value(metadataFile, "mrsigner->value:")
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

    class SigningEnclave : EnclaveCall, Enclave() {
        override fun invoke(bytes: ByteArray): ByteArray {
            val signature = signer().run {
                update(bytes)
                sign()
            }
            return writeData {
                writeIntLengthPrefixBytes(signatureKey.encoded)
                writeIntLengthPrefixBytes(signature)
            }
        }
    }

    class RepeatedOcallEnclave : EnclaveCall, Enclave() {
        override fun invoke(bytes: ByteArray): ByteArray? {
            val count = bytes.toInt()
            repeat(count) { index ->
                callUntrustedHost(index.toByteArray())
            }
            return null
        }
    }

    class AddingEnclave : EnclaveCall, Enclave() {
        private var maxCallCount: Int? = null
        private val sum = AtomicInteger(0)
        private val callCount = AtomicInteger(0)

        override fun invoke(bytes: ByteArray): ByteArray? {
            val number = bytes.toInt()
            if (maxCallCount == null) {
                maxCallCount = number
            } else {
                val sum = sum.addAndGet(number)
                if (callCount.incrementAndGet() == maxCallCount) {
                    return sum.toByteArray()
                }
            }
            return null
        }
    }

    class ThrowingEnclave : EnclaveCall, Enclave() {
        companion object {
            const val CHEERS = "You are all wrong"
        }

        override fun invoke(bytes: ByteArray): ByteArray? {
            throw RuntimeException(CHEERS)
        }
    }

    class RecursingEnclave : EnclaveCall, Enclave() {
        override fun invoke(bytes: ByteArray): ByteArray? {
            var remaining = bytes.toInt()
            while (remaining > 0) {
                remaining = callUntrustedHost((remaining - 1).toByteArray())!!.toInt()
            }
            return null
        }
    }
}

private fun Int.toByteArray(): ByteArray = ByteBuffer.allocate(4).putInt(this).array()

private fun ByteArray.toInt(): Int = ByteBuffer.wrap(this).getInt()
