package com.r3.conclave.integrationtests.general.tests.plugin

import org.junit.jupiter.api.Test

class SignEnclaveWithKeyTest : AbstractModeTaskTest() {
    override val baseTaskName: String get() ="signEnclaveWithKey"
    override val outputName: String get() = "enclave.signed.so"

    @Test
    fun `asd`() {

    }
}
