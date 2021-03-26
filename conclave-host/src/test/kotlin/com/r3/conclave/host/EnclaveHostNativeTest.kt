package com.r3.conclave.host

import com.r3.conclave.enclave.Enclave
import com.r3.conclave.internaltesting.RecordingCallback
import com.r3.conclave.internaltesting.dynamic.EnclaveBuilder
import com.r3.conclave.internaltesting.dynamic.EnclaveConfig
import com.r3.conclave.internaltesting.dynamic.TestEnclaves
import com.r3.conclave.mail.PostOffice
import com.r3.conclave.testing.internal.EnclaveMetadata
import com.r3.conclave.utilities.internal.dataStream
import com.r3.conclave.utilities.internal.readIntLengthPrefixBytes
import com.r3.conclave.utilities.internal.writeData
import com.r3.conclave.utilities.internal.writeIntLengthPrefixBytes
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
    fun `enclave info`() {
        start<StatefulEnclave>()
        val metadataFile = testEnclaves.getEnclaveMetadata(StatefulEnclave::class.java, EnclaveBuilder())
        val enclaveMetadata = EnclaveMetadata.parseMetadataFile(metadataFile)
        host.enclaveInstanceInfo.enclaveInfo.apply {
            assertThat(codeHash).isEqualTo(enclaveMetadata.mrenclave)
            assertThat(codeSigningKeyHash).isEqualTo(enclaveMetadata.mrsigner)
        }
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
}

private fun Int.toByteArray(): ByteArray = ByteBuffer.allocate(4).putInt(this).array()

private fun ByteArray.toInt(): Int = ByteBuffer.wrap(this).getInt()
