package com.r3.conclave.testing.internal

import com.r3.conclave.common.internal.KeyType
import com.r3.conclave.enclave.Enclave
import com.r3.conclave.enclave.internal.EnclaveEnvironment
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.nio.ByteBuffer

class MockEnclaveEnvironmentTest {
    class EnclaveA : Enclave()

    class EnclaveB : Enclave()

    @Test
    fun `MockEnclaveEnvironment defaultSealingKey MRENCLAVE`() {
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
    fun `MockEnclaveEnvironment defaultSealingKey MRSIGNER`() {
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
    private fun runMockKeyRequests(env: EnclaveEnvironment, keyTypes: Array<KeyType>, useSigner: Boolean): List<ByteBuffer> {
        return keyTypes.map { keyType ->
            ByteBuffer.wrap(env.defaultSealingKey(keyType, useSigner)).also { assertEquals(it.capacity(), 16) }
        }
    }

    private inline fun <reified E : Enclave> createMockEnclaveEnvironment(): EnclaveEnvironment {
        return MockEnclaveEnvironment(E::class.java.getConstructor().newInstance())
    }
}
