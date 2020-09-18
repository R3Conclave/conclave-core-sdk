package com.r3.conclave.integrationtests.djvm.enclave

import com.r3.conclave.enclave.internal.memory.MemoryURL
import com.r3.conclave.integrationtests.djvm.base.EnclaveJvmTest

interface TestEnvironment {
    fun setup(userJars: List<MemoryURL>)

    fun destroy()

    fun runTest(test: EnclaveJvmTest, input: Any?): Any?
}
