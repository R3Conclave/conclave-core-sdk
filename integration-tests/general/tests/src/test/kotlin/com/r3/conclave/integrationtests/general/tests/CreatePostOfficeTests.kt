package com.r3.conclave.integrationtests.general.tests

import org.assertj.core.api.Assertions.assertThatIllegalStateException
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

class CreatePostOfficeTests : JvmTest("com.r3.conclave.integrationtests.general.enclave.NonThreadSafeEnclaveWithPostOffice") {

    @ParameterizedTest
    @ValueSource(strings = ["PostOffice.create()", "EnclaveInstanceInfo.createPostOffice()"])
    fun `cannot create PostOffice directly when inside enclave`(source: String) {
        assertThatIllegalStateException()
            .isThrownBy { enclaveHost.callEnclave(source.toByteArray()) }
            .withMessage("Use one of the Enclave.postOffice() methods for getting a PostOffice instance when inside an enclave.")
    }
}
