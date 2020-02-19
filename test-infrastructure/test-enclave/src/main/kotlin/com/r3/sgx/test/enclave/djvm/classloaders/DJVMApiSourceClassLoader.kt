package com.r3.sgx.test.enclave.djvm.classloaders

import com.r3.sgx.utils.classloaders.MemoryClassLoader
import com.r3.sgx.utils.classloaders.MemoryURL
import net.corda.djvm.source.ApiSource

/**
 * ClassLoader needed to provide the DJVM with the Java APIs
 * @param memoryUrls list of [MemoryURL] with the files sent by the host
 */
class DJVMApiSourceClassLoader(memoryUrls: List<MemoryURL>) : MemoryClassLoader(memoryUrls), ApiSource {
    override fun close() {
    }
}