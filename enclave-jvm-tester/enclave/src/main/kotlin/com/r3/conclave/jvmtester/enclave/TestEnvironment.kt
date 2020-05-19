package com.r3.conclave.jvmtester.enclave

import com.r3.conclave.jvmtester.api.EnclaveJvmTest
import com.r3.conclave.utils.classloaders.MemoryURL

interface TestEnvironment {
    fun setup(userJars: List<MemoryURL>)

    fun destroy()

    fun runTest(test: EnclaveJvmTest, input: Any?): Any?
}
