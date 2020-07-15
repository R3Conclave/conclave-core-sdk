package com.r3.conclave.testing.internal

import com.r3.conclave.common.OpaqueBytes
import com.r3.conclave.common.internal.KeyType
import com.r3.conclave.common.internal.PlaintextAndEnvelope
import com.r3.conclave.enclave.Enclave
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatExceptionOfType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import java.nio.ByteBuffer
import java.security.GeneralSecurityException

class MockEnclaveEnvironmentTest {
    private companion object {
        private val plaintext = OpaqueBytes("Super Secret!".toByteArray())
        private val authenticatedData = OpaqueBytes("Authenticate!".toByteArray())
    }

    class EnclaveA : Enclave()

    class EnclaveB : Enclave()

    @ParameterizedTest(name = "{displayName} {argumentsWithNames}")
    @ValueSource(booleans = [true, false])
    fun `seal and unseal on same enclave instance`(useAuthenticatedData: Boolean) {
        val env = createMockEnclaveEnvironment<EnclaveA>()
        val input = PlaintextAndEnvelope(plaintext, authenticatedData.takeIf { useAuthenticatedData })
        val sealedBlob = env.sealData(input)
        assertThat(env.unsealData(sealedBlob)).isEqualTo(input)
    }

    @ParameterizedTest(name = "{displayName} {argumentsWithNames}")
    @ValueSource(booleans = [true, false])
    fun `seal and unseal on different instance of same enclave`(useAuthenticatedData: Boolean) {
        val env1 = createMockEnclaveEnvironment<EnclaveA>()
        val env2 = createMockEnclaveEnvironment<EnclaveA>()
        val input = PlaintextAndEnvelope(plaintext, authenticatedData.takeIf { useAuthenticatedData })
        val sealedBlob = env1.sealData(input)
        assertThat(env2.unsealData(sealedBlob)).isEqualTo(input)
    }

    @ParameterizedTest(name = "{displayName} {argumentsWithNames}")
    @ValueSource(booleans = [true, false])
    fun `seal and unseal on different enclaves`(useAuthenticatedData: Boolean) {
        val envA = createMockEnclaveEnvironment<EnclaveA>()
        val envB = createMockEnclaveEnvironment<EnclaveB>()
        val input = PlaintextAndEnvelope(plaintext, authenticatedData.takeIf { useAuthenticatedData })
        val sealedBlob = envA.sealData(input)
        assertThatExceptionOfType(GeneralSecurityException::class.java).isThrownBy {
            envB.unsealData(sealedBlob)
        }
    }

    @Test
    fun `sealed data is different on same plaintext`() {
        val env = createMockEnclaveEnvironment<EnclaveA>()
        val sealedBlob1 = env.sealData(PlaintextAndEnvelope(plaintext))
        val sealedBlob2 = env.sealData(PlaintextAndEnvelope(plaintext))
        assertThat(sealedBlob1).isNotEqualTo(sealedBlob2)
    }

    @Test
    fun `defaultSealingKey MRENCLAVE`() {
        val envEnclaveA1 = createMockEnclaveEnvironment<EnclaveA>()
        val envEnclaveA2 = createMockEnclaveEnvironment<EnclaveA>()
        val envEnclaveB = createMockEnclaveEnvironment<EnclaveB>()
        val keyTypes = enumValues<KeyType>()
        val enclaveA1MRENCLAVEKeys = runMockKeyRequests(envEnclaveA1, keyTypes, false)
        val enclaveA2MRENCLAVEKeys = runMockKeyRequests(envEnclaveA2, keyTypes, false)
        val enclaveBMRENCLAVEKeys = runMockKeyRequests(envEnclaveB, keyTypes, false)
        // Check if all keys generated are distinct from each other.
        val keysUniqueAMRENCLAVE = enclaveA1MRENCLAVEKeys.distinct()
        assertEquals(keysUniqueAMRENCLAVE.size, enclaveA1MRENCLAVEKeys.size)
        val keysUniqueBMRENCLAVE = enclaveBMRENCLAVEKeys.distinct()
        assertEquals(keysUniqueBMRENCLAVE.size, enclaveBMRENCLAVEKeys.size)
        //
        assertEquals(enclaveA1MRENCLAVEKeys, enclaveA2MRENCLAVEKeys) // Keys of the same enclave should match.
        // Keys of type MRENCLAVE and MRSIGNER shouldn't match.
        enclaveA1MRENCLAVEKeys.forEach { key1 ->
            enclaveBMRENCLAVEKeys.forEach { key2 ->
                assertNotEquals(key1, key2)
            }
        }
    }

    @Test
    fun `defaultSealingKey MRSIGNER`() {
        val envEnclaveA1 = createMockEnclaveEnvironment<EnclaveA>()
        val envEnclaveA2 = createMockEnclaveEnvironment<EnclaveA>()
        val envEnclaveB = createMockEnclaveEnvironment<EnclaveB>()
        val keyTypes = enumValues<KeyType>()
        val enclaveA1MRSIGNERKeys = runMockKeyRequests(envEnclaveA1, keyTypes, true)
        val enclaveA2MRSIGNERKeys = runMockKeyRequests(envEnclaveA2, keyTypes, true)
        val enclaveBMRSIGNERKeys = runMockKeyRequests(envEnclaveB, keyTypes, true)
        // Check if all keys generated are distinct from each other.
        val keysUniqueAMRSIGNER = enclaveA1MRSIGNERKeys.distinct()
        assertEquals(keysUniqueAMRSIGNER.size, enclaveA1MRSIGNERKeys.size)
        val keysUniqueBMRSIGNER = enclaveBMRSIGNERKeys.distinct()
        assertEquals(keysUniqueBMRSIGNER.size, enclaveBMRSIGNERKeys.size)
        //
        // All MRSIGNER keys should match.
        assertEquals(enclaveA1MRSIGNERKeys, enclaveA2MRSIGNERKeys)
        assertEquals(enclaveA1MRSIGNERKeys, enclaveBMRSIGNERKeys)
        //
    }

    /**
     * Helper function to retrieve defaultSealingKeys.
     * @param env instance of the enclave environment to execute the request.
     * @param keyTypes keys to be requested.
     * @param useSigner true = MRSIGNER, false = MRENCLAVE.
     *  default = requestReportKey, requestSealMRSignerKey, requestSealMREnclaveKey
     * @return List<ByteBuffer> containing the requested keys.
     */
    private fun runMockKeyRequests(env: MockEnclaveEnvironment, keyTypes: Array<KeyType>, useSigner: Boolean): List<ByteBuffer> {
        return keyTypes.map { keyType ->
            ByteBuffer.wrap(env.defaultSealingKey(keyType, useSigner)).also { assertEquals(it.capacity(), 16) }
        }
    }

    private inline fun <reified E : Enclave> createMockEnclaveEnvironment(): MockEnclaveEnvironment {
        return MockEnclaveEnvironment(E::class.java.getConstructor().newInstance())
    }
}
