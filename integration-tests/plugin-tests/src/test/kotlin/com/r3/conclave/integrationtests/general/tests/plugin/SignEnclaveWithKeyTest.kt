package com.r3.conclave.integrationtests.general.tests.plugin

import com.r3.conclave.integrationtests.general.commontest.TestUtils.graalvmOnlyTest
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach

class SignEnclaveWithKeyTest : AbstractModeTaskTest() {
    companion object {
        @JvmStatic
        @BeforeAll
        fun check() {
            graalvmOnlyTest()
        }
    }

    override val baseTaskName: String get() ="signEnclaveWithKey"
    override val outputName: String get() = "enclave.signed.so"
    /**
     * Native image doesn't produce stable binaries.
     */
    override val isReproducible: Boolean get() = false

    @BeforeEach
    fun copyUnsignedEnclave() {
        copyPrebuiltUnsignedGraalEnclave()
    }


}
