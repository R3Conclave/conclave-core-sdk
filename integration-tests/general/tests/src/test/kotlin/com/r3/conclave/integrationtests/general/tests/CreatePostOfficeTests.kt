package com.r3.conclave.integrationtests.general.tests

import com.r3.conclave.integrationtests.general.common.tasks.CreatePostOffice
import com.r3.conclave.integrationtests.general.commontest.AbstractEnclaveActionTest
import com.r3.conclave.integrationtests.general.commontest.TestUtils.graalvmOnlyTest
import org.assertj.core.api.Assertions.assertThatIllegalStateException
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

class CreatePostOfficeTests : AbstractEnclaveActionTest() {
    @ParameterizedTest
    @ValueSource(strings = ["PostOffice.create()", "EnclaveInstanceInfo.createPostOffice()"])
    fun `cannot create PostOffice directly when inside enclave`(source: String) {
        graalvmOnlyTest() // CON-1271: Test creating PostOffice in the enclave fails in Gramine
        assertThatIllegalStateException()
            .isThrownBy { callEnclave(CreatePostOffice(source)) }
            .withMessage("Use one of the Enclave.postOffice() methods for getting a PostOffice instance when inside an enclave.")
    }
}
