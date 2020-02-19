package com.r3.sgx.test.enclave.djvm.classloaders

import com.r3.sgx.utils.classloaders.MemoryClassLoader
import com.r3.sgx.utils.classloaders.MemoryURL
import net.corda.djvm.source.UserSource

/**
 * ClassLoader needed to provide the DJVM with the user code
 * @param memoryUrls list of [MemoryURL] with the files sent by the host
 */
class DJVMUserSourceClassLoader(memoryUrls: List<MemoryURL>) : MemoryClassLoader(memoryUrls), UserSource {
    override fun close() {
    }
}