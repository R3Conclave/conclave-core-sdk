package com.r3.conclave.integrationtests.djvm.enclave.sandbox

import com.r3.conclave.enclave.internal.memory.MemoryClassLoader
import com.r3.conclave.enclave.internal.memory.MemoryURL
import net.corda.djvm.source.UserSource

/**
 * ClassLoader needed to provide the DJVM with the user code
 * @param memoryUrls list of [MemoryURL] with the files sent by the host
 */
class DJVMUserSourceClassLoader(memoryUrls: List<MemoryURL>) : MemoryClassLoader(memoryUrls), UserSource {
    override fun close() {
    }
}