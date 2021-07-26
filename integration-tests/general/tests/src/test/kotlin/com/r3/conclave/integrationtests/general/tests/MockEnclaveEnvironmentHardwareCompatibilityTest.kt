package com.r3.conclave.integrationtests.general.tests

import com.google.common.collect.Sets
import com.r3.conclave.common.EnclaveMode
import com.r3.conclave.common.MockConfiguration
import com.r3.conclave.common.OpaqueBytes
import com.r3.conclave.common.internal.*
import com.r3.conclave.common.internal.handler.ThrowingErrorHandler
import com.r3.conclave.enclave.Enclave
import com.r3.conclave.enclave.internal.EnclaveEnvironment
import com.r3.conclave.host.EnclaveHost
import com.r3.conclave.host.internal.InternalsKt.createMockHost
import com.r3.conclave.host.internal.MockEnclaveHandle
import com.r3.conclave.integrationtests.general.common.*
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import java.lang.IllegalStateException
import kotlin.random.Random

/**
 * Tests to make sure secret keys produced by [MockEnclaveEnvironment.getSecretKey] behave similarly to ones produced
 * in hardware mode.
 */
private const val enclave1 = "com.r3.conclave.integrationtests.general.enclave.SecretKeyEnclave1"
private const val enclave2 = "com.r3.conclave.integrationtests.general.enclave.SecretKeyEnclave2"

class MockEnclaveEnvironmentHardwareCompatibilityTest : HardwareTest {
    companion object {

        // A list of all the SecretKeySpecs that we want to test for, created by a cartesian product of the various
        // key request parameters we're interested in.
        private val secretKeySpecs: List<SecretKeySpec> = Sets.cartesianProduct(
            // Create keys across two different enclaves, ...
            setOf(EnclaveName(enclave1), EnclaveName(enclave2)),
            // ... which have one of two IsvProdId values
            setOf(EnclaveIsvProdId(10), EnclaveIsvProdId(20)),
            // ... and one of two IsvSvn values.
            setOf(EnclaveIsvSvn(2), EnclaveIsvSvn(3)),
            // Create either REPORT or SEAL keys. The other key types are not relevant.
            setOf(KeyNameField(KeyName.REPORT), KeyNameField(KeyName.SEAL)),
            // Create keys with all 8 possible policy combinations by using MRENCLAVE, MRSIGNER and NOISVPRODID.
            // The other key policies are not relevant as we don't enable KSS.
            Sets.powerSet(setOf(KeyPolicy.MRENCLAVE, KeyPolicy.MRSIGNER, KeyPolicy.NOISVPRODID)).map(::KeyPolicyField)
                .toSet(),
            // Create keys with no ISVSVN, ISVSVN set to the enclave's and with values on either side.
            setOf(BlankField(SgxKeyRequest::isvSvn), IsvSvnFieldDelta(-1), IsvSvnFieldDelta(0), IsvSvnFieldDelta(1)),
            // Create keys with no CPUSVN, CPUSVN set to the current value and a random one.
            setOf(
                BlankField(SgxKeyRequest::cpuSvn),
                CpuSvnField(null),
                CpuSvnField(OpaqueBytes(Random.nextBytes(SgxCpuSvn.INSTANCE.size)))
            ),
            // Create keys with no key ID and with a random one.
            setOf(BlankField(SgxKeyRequest::keyId), KeyIdField(OpaqueBytes(Random.nextBytes(SgxKeyId.INSTANCE.size))))
        ).map(::SecretKeySpec)
    }

    private val nativeEnclaves = HashMap<EnclaveSpec, EnclaveHost>()
    private val mockEnclaves = HashMap<EnclaveSpec, EnclaveHost>()

    @AfterEach
    fun cleanUp() {
        nativeEnclaves.values.forEach(EnclaveHost::close)
    }

    @Test
    fun `mock secret keys have the same uniqueness as hardware secret keys`() {
        val nativeUniqueness = KeyUniquenessContainer(::getNativeHost)
        val mockUniqueness = KeyUniquenessContainer(::getMockHost)

        for (spec in secretKeySpecs) {
            nativeUniqueness.queryAndDetermineKeyUniqueness(spec)
            mockUniqueness.queryAndDetermineKeyUniqueness(spec)
        }

        println("Key requests producing the same key:")
        println("====================================")
        nativeUniqueness.keyToSameKeyGroup.values.forEach { group ->
            group.forEach(::println)
            println()
        }
        println()

        println("Key requests producing errors:")
        println("==============================")
        nativeUniqueness.errorKeyRequests.forEach { keyRequestSpec, message ->
            println("$keyRequestSpec: $message")
        }
        println()

        // If native throws then mock must throw with the same message.
        nativeUniqueness.errorKeyRequests.forEach { keyRequestSpec, nativeError ->
            val mockError = mockUniqueness.errorKeyRequests[keyRequestSpec]
            assertNotNull(mockError) { "$keyRequestSpec returned a key but should have returned error '$nativeError'" }
            assertEquals(nativeError, mockError, keyRequestSpec::toString)
        }

        // We don't care for the native and mock keys to be the same (in fact that's not possible), but we do care that
        // the group of key requests which produce the same key is the same across native and mock. This means, for
        // example, if a native key request produces a unique key then so must the mock key request, and if a native key
        // request produces a non-unique key then the set of key requests which produce that same key is the same across
        // native and mock.
        for ((keyRequestSpec, mockGroup) in mockUniqueness.keyRequestToSameKeyGroup) {
            val nativeGroup = nativeUniqueness.keyRequestToSameKeyGroup.getValue(keyRequestSpec)
            assertThat(mockGroup).describedAs(keyRequestSpec.toString()).isEqualTo(nativeGroup)
        }
    }

    private fun getNativeHost(enclaveSpec: EnclaveSpec): EnclaveHost {
        return nativeEnclaves.computeIfAbsent(enclaveSpec) {
            val host = EnclaveHost.load(enclaveSpec.enclaveName)

            val attestationParameters = when(host.enclaveMode) {
                EnclaveMode.RELEASE, EnclaveMode.DEBUG -> JvmTest.getHardwareAttestationParams()
                else -> throw IllegalStateException("The enclave needs to be built in Release or Debug mode")
            }

            host.start(attestationParameters, null)
            host
        }
    }

    private fun getMockHost(enclaveSpec: EnclaveSpec): EnclaveHost {
        return mockEnclaves.computeIfAbsent(enclaveSpec) {
            val mockConfiguration = MockConfiguration()
            mockConfiguration.productID = enclaveSpec.isvProdId
            mockConfiguration.revocationLevel = enclaveSpec.isvSvn - 1
            val host = createMockHost(Class.forName(enclaveSpec.enclaveName), mockConfiguration)

            host.start(null, null)
            host
        }
    }

    class KeyUniquenessContainer(private val hostLookup: (EnclaveSpec) -> EnclaveHost) {
        val keyToSameKeyGroup = LinkedHashMap<OpaqueBytes, MutableList<SecretKeySpec>>()
        val keyRequestToSameKeyGroup = HashMap<SecretKeySpec, List<SecretKeySpec>>()
        val errorKeyRequests = HashMap<SecretKeySpec, String>()

        fun queryAndDetermineKeyUniqueness(spec: SecretKeySpec) {
            val result = spec.querySecretKey(hostLookup)
            assertEquals(spec.querySecretKey(hostLookup), result, "Same key request must produce same key")
            when (result) {
                is Result.Key -> {
                    assertThat(result.bytes.size).isEqualTo(SgxKey128Bit.INSTANCE.size)
                    val keyRequestGroup = keyToSameKeyGroup.computeIfAbsent(result.bytes) { ArrayList() }
                    keyRequestGroup += spec
                    keyRequestToSameKeyGroup[spec] = keyRequestGroup
                }
                is Result.Error -> {
                    errorKeyRequests[spec] = result.message
                }
            }
        }
    }
}
