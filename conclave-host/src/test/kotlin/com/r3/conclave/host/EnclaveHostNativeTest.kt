package com.r3.conclave.host

import com.r3.conclave.common.SHA256Hash
import com.r3.conclave.common.SecureHash
import com.r3.conclave.common.enclave.EnclaveCall
import com.r3.conclave.enclave.Enclave
import com.r3.sgx.dynamictesting.EnclaveBuilder
import com.r3.sgx.dynamictesting.TestEnclaves
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path
import kotlin.streams.asSequence

class EnclaveHostNativeTest {
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
        host.start(null, null)

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

    @Test
    fun `enclave info`() {
        val host = hostTo<StatefulEnclave>()
        val metadataFile = testEnclaves.getEnclaveMetadata(StatefulEnclave::class.java, EnclaveBuilder())
        host.start(null, null)
        host.enclaveInstanceInfo.enclaveInfo.apply {
            assertThat(codeHash).isEqualTo(getMeasurement(metadataFile))
            assertThat(codeSigningKeyHash).isEqualTo(getMrsigner(metadataFile))
        }
    }

    private inline fun <reified T : Enclave> hostTo(): EnclaveHost {
        val enclaveBuilder = EnclaveBuilder()
        val enclaveFile = testEnclaves.getEnclave(T::class.java, enclaveBuilder).toPath()
        return EnclaveHost.create(enclaveFile, EnclaveMode.SIMULATION, tempFile = false)
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
}
