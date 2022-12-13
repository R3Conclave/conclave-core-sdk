package com.r3.conclave.enclave.internal

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatExceptionOfType
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import java.nio.ByteBuffer
import java.security.SecureRandom
import java.util.concurrent.Executors
import javax.crypto.AEADBadTagException

class EnclaveUtilsTest {
    private val aesKey = randomBytes(16)
    private val plaintext = randomBytes(1024)
    private val authenticatedData = randomBytes(128)
    private val toBeSealed = PlaintextAndEnvelope(plaintext, authenticatedData)

    @ParameterizedTest
    @ValueSource(booleans = [false, true])
    fun `sealed blob and can be unsealed using same key`(useAuthenticatedData: Boolean) {
        val toBeSealed = PlaintextAndEnvelope(plaintext, authenticatedData.takeIf { useAuthenticatedData })
        val sealedBlob = EnclaveUtils.sealData(aesKey, toBeSealed)
        val unsealed = EnclaveUtils.unsealData(aesKey, ByteBuffer.wrap(sealedBlob))
        assertThat(unsealed).isEqualTo(toBeSealed)
    }

    @Test
    fun `sealing same plaintext twice does not produce same ciphertext`() {
        assertThat(EnclaveUtils.sealData(aesKey, toBeSealed)).isNotEqualTo(EnclaveUtils.sealData(aesKey, toBeSealed))
    }

    @Test
    fun `sealed blob cannot be unsealed using different key`() {
        val sealedBlob = EnclaveUtils.sealData(aesKey, toBeSealed)
        for (index in aesKey.indices) {
            aesKey[index]--
            assertThatExceptionOfType(AEADBadTagException::class.java).isThrownBy {
                EnclaveUtils.unsealData(aesKey, ByteBuffer.wrap(sealedBlob))
            }
            aesKey[index]++
        }
    }

    @Test
    fun `corrupted sealed blob cannot be unsealed`() {
        val sealedBlob = EnclaveUtils.sealData(aesKey, toBeSealed)
        for (index in sealedBlob.indices) {
            sealedBlob[index]--
            assertThrows<Exception> { EnclaveUtils.unsealData(aesKey, ByteBuffer.wrap(sealedBlob)) }
            sealedBlob[index]++
        }
    }

    @Test
    fun `thread safety`() {
        val executor = Executors.newWorkStealingPool()

        val concurrentTasks = Array(100) {
            executor.submit {
                repeat(100) {
                    val aesKey = randomBytes(16)
                    val sealedBlob = EnclaveUtils.sealData(aesKey, toBeSealed)
                    val unsealed = EnclaveUtils.unsealData(aesKey, ByteBuffer.wrap(sealedBlob))
                    assertThat(unsealed).isEqualTo(toBeSealed)
                }
            }
        }

        concurrentTasks.forEach { it.get() }
        executor.shutdownNow()
    }

    private fun randomBytes(size: Int): ByteArray = ByteArray(size).also(SecureRandom()::nextBytes)
}
