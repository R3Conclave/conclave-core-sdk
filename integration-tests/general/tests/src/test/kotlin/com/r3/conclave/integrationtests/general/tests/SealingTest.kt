package com.r3.conclave.integrationtests.general.tests

import com.r3.conclave.common.SecureHash
import com.r3.conclave.host.EnclaveHost
import com.r3.conclave.integrationtests.general.common.PlaintextAndAD
import com.r3.conclave.integrationtests.general.common.tasks.SealData
import com.r3.conclave.integrationtests.general.common.tasks.UnsealData
import com.r3.conclave.integrationtests.general.commontest.AbstractEnclaveActionTest
import com.r3.conclave.integrationtests.general.commontest.TestUtils
import com.r3.conclave.integrationtests.general.commontest.TestUtils.graalvmOnlyTest
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatExceptionOfType
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

class SealingTest : AbstractEnclaveActionTest("com.r3.conclave.integrationtests.general.threadsafeenclave.ThreadSafeEnclave") {
    @ParameterizedTest
    @ValueSource(booleans = [ false, true ])
    fun `seal and unseal with same enclave instance`(withAD: Boolean) {
        val data = PlaintextAndAD("Sealing Hello World!".toByteArray(), if (withAD) "with AD!".toByteArray() else null)
        val sealedBlob = callEnclave(SealData(data))
        val roundtrip = callEnclave(UnsealData(sealedBlob))
        assertThat(roundtrip).isEqualTo(data)
    }

    @ParameterizedTest
    @ValueSource(booleans = [ false, true ])
    fun `unseal with different instance of same enclave`(withAD: Boolean) {
        val differentInstanceOfSameEnclave = EnclaveHost.load(enclaveHost().enclaveClassName)
        differentInstanceOfSameEnclave.let { it.start(TestUtils.getAttestationParams(it), null, null) { } }

        val data = PlaintextAndAD("Sealing Hello World!".toByteArray(), if (withAD) "with AD!".toByteArray() else null)
        val sealedBlob = callEnclave(enclaveHost(), SealData(data))
        val roundtrip = differentInstanceOfSameEnclave.use { callEnclave(it, UnsealData(sealedBlob)) }
        assertThat(roundtrip).isEqualTo(data)
    }

    @ParameterizedTest
    @ValueSource(booleans = [ false, true ])
    fun `unseal with different enclave of same MRSIGNER`(withAD: Boolean) {
        graalvmOnlyTest() // CON-1265: "javax.crypto.AEADBadTagException: Tag mismatch!" thrown when unsealing data in Gramine
        val sameMrsignerEnclave = enclaveHost("com.r3.conclave.integrationtests.general.threadsafeenclave.ThreadSafeEnclaveSameSigner")
        assertThat(enclaveHost().mrsigner).isEqualTo(sameMrsignerEnclave.mrsigner)

        val data = PlaintextAndAD("Sealing Hello World!".toByteArray(), if (withAD) "with AD!".toByteArray() else null)
        val sealedBlob = callEnclave(enclaveHost(), SealData(data))
        val roundtrip = callEnclave(sameMrsignerEnclave, UnsealData(sealedBlob))
        assertThat(roundtrip).isEqualTo(data)
    }

    @ParameterizedTest
    @ValueSource(booleans = [ false, true ])
    fun `unseal with enclave of different MRSIGNER`(withAD: Boolean) {
        graalvmOnlyTest() // CON-1265: "javax.crypto.AEADBadTagException: Tag mismatch!" thrown when unsealing data in Gramine
        val differentMrsignerEnclave = enclaveHost("com.r3.conclave.integrationtests.general.defaultenclave.DefaultEnclave")
        assertThat(enclaveHost().mrsigner).isNotEqualTo(differentMrsignerEnclave.mrsigner)

        val data = PlaintextAndAD("Sealing Hello World!".toByteArray(), if (withAD) "with AD!".toByteArray() else null)
        val sealedBlob = callEnclave(enclaveHost(), SealData(data))

        assertThatExceptionOfType(RuntimeException::class.java).isThrownBy {
            callEnclave(differentMrsignerEnclave, UnsealData(sealedBlob))
        }.withMessageContaining("SGX_ERROR_MAC_MISMATCH")
    }

    private val EnclaveHost.mrsigner: SecureHash get() = enclaveInstanceInfo.enclaveInfo.codeSigningKeyHash
}
