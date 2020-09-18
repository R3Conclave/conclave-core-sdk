package com.r3.conclave.integrationtests.djvm.enclave.sandbox

import com.r3.conclave.enclave.internal.memory.MemoryClassLoader
import com.r3.conclave.enclave.internal.memory.MemoryURL
import net.corda.djvm.source.ApiSource

/**
 * ClassLoader needed to provide the DJVM with the Java APIs
 * @param memoryUrls list of [MemoryURL] with the files sent by the host
 */
class DJVMApiSourceClassLoader(memoryUrls: List<MemoryURL>) : MemoryClassLoader(memoryUrls), ApiSource {
    override fun close() {
    }
}