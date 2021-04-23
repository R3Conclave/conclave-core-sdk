package com.r3.conclave.host

import com.r3.conclave.common.SHA256Hash
import com.r3.conclave.common.SecureHash
import com.r3.conclave.enclave.Enclave
import com.r3.conclave.internaltesting.RecordingCallback
import com.r3.conclave.internaltesting.dynamic.EnclaveBuilder
import com.r3.conclave.internaltesting.dynamic.EnclaveConfig
import com.r3.conclave.internaltesting.dynamic.TestEnclaves
import com.r3.conclave.internaltesting.dynamic.EnclaveType
import com.r3.conclave.mail.PostOffice
import com.r3.conclave.testing.internal.EnclaveMetadata
import com.r3.conclave.utilities.internal.dataStream
import com.r3.conclave.utilities.internal.readIntLengthPrefixBytes
import com.r3.conclave.utilities.internal.writeData
import com.r3.conclave.utilities.internal.writeIntLengthPrefixBytes
import com.r3.conclave.utilities.internal.digest
import org.assertj.core.api.Assertions.*
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import java.nio.ByteBuffer
import java.util.concurrent.atomic.AtomicInteger
import java.util.function.Function
import java.util.stream.IntStream
import kotlin.streams.toList

class EnclaveHostLocalTest {
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
        start<StatefulEnclave>(EnclaveBuilder(type = EnclaveType.Mock))

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
        start<StatefulEnclave>(EnclaveBuilder(type = EnclaveType.Mock))
        val mrenclave = SHA256Hash.hash(EnclaveHostLocalTest.StatefulEnclave::class.java.name.toByteArray())
        val mrsigner = SHA256Hash.wrap(ByteArray(32))
        host.enclaveInstanceInfo.enclaveInfo.apply {
            assertThat(codeHash).isEqualTo(mrenclave)
            assertThat(codeSigningKeyHash).isEqualTo(mrsigner)
        }
    }

    @Test
    fun `enclave signing key`() {
        start<SigningEnclave>(EnclaveBuilder(type = EnclaveType.Mock))
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
        start<RepeatedOcallEnclave>(EnclaveBuilder(type = EnclaveType.Mock))
        val ocallResponses = RecordingCallback()
        host.callEnclave(100.toByteArray(), ocallResponses)
        assertThat(ocallResponses.calls.map { it.toInt() }).isEqualTo(IntArray(100) { it }.asList())
    }

    @Test
    fun `concurrent calls into the enclave`() {
        val n = 10000
        start<AddingEnclave>(EnclaveBuilder(type = EnclaveType.Mock, config = EnclaveConfig().withTCSNum(20)))
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
        start<RepeatedOcallEnclave>(EnclaveBuilder(type = EnclaveType.Mock, config = EnclaveConfig().withTCSNum(20)))
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
        start<ThrowingEnclave>(EnclaveBuilder(type = EnclaveType.Mock))
        assertThatExceptionOfType(RuntimeException::class.java).isThrownBy {
            host.callEnclave(byteArrayOf())
        }.withMessage(ThrowingEnclave.CHEERS)
    }

    @Test
    fun `ECALL-OCALL recursion`() {
        start<RecursingEnclave>(EnclaveBuilder(type = EnclaveType.Mock))
        val callback = object : Function<ByteArray, ByteArray?> {
            var called = 0
            override fun apply(bytes: ByteArray): ByteArray {
                called++
                val response = bytes.toInt() - 1
                return response.toByteArray()
            }
        }
        host.callEnclave(100.toByteArray(), callback)
        assertThat(callback.called).isEqualTo(50)
    }

    @ParameterizedTest
    @ValueSource(strings = ["PostOffice.create()", "EnclaveInstanceInfo.createPostOffice()"])
    fun `cannot create PostOffice directly when inside enclave`(source: String) {
        class CreatePostOfficeEnclave : Enclave() {
            override fun receiveFromUntrustedHost(bytes: ByteArray): ByteArray? {
                when (String(bytes)) {
                    "PostOffice.create()" -> PostOffice.create(enclaveInstanceInfo.encryptionKey)
                    "EnclaveInstanceInfo.createPostOffice()" -> enclaveInstanceInfo.createPostOffice()
                }
                return null
            }
        }

        start<CreatePostOfficeEnclave>(EnclaveBuilder(type = EnclaveType.Mock))

        assertThatIllegalStateException()
            .isThrownBy { host.callEnclave(source.toByteArray()) }
            .withMessage("Use one of the Enclave.postOffice() methods for getting a PostOffice instance when inside an enclave.")
    }

    private inline fun <reified T : Enclave> start(enclaveBuilder: EnclaveBuilder = EnclaveBuilder()) {
        host = testEnclaves.hostTo<T>(enclaveBuilder).apply {
            start(null, null)
        }
    }

    class StatefulEnclave : Enclave() {
        private var previousResult = ""

        override fun receiveFromUntrustedHost(bytes: ByteArray): ByteArray {
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

    class SigningEnclave : Enclave() {
        override fun receiveFromUntrustedHost(bytes: ByteArray): ByteArray {
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

    class RepeatedOcallEnclave : Enclave() {
        override fun receiveFromUntrustedHost(bytes: ByteArray): ByteArray? {
            val count = bytes.toInt()
            repeat(count) { index ->
                callUntrustedHost(index.toByteArray())
            }
            return null
        }
    }

    class AddingEnclave : Enclave() {
        private var maxCallCount: Int? = null
        private val sum = AtomicInteger(0)
        private val callCount = AtomicInteger(0)

        override fun receiveFromUntrustedHost(bytes: ByteArray): ByteArray? {
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

    class ThrowingEnclave : Enclave() {
        companion object {
            const val CHEERS = "You are all wrong"
        }

        override fun receiveFromUntrustedHost(bytes: ByteArray): ByteArray? {
            throw RuntimeException(CHEERS)
        }
    }

    class RecursingEnclave : Enclave() {
        override fun receiveFromUntrustedHost(bytes: ByteArray): ByteArray? {
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
