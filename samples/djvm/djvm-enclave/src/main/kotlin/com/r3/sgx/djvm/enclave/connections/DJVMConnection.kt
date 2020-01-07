package com.r3.sgx.djvm.enclave.connections

import com.r3.sgx.core.common.Sender
import com.r3.sgx.utils.classloaders.MemoryURL
import java.nio.ByteBuffer
import java.util.function.Consumer

class DJVMConnection(private val upstream: Sender) : Sender {
    /**
     * Jars received by [com.r3.sgx.djvm.enclave.handlers.JarHandler] will be added to this list and
     * setup in the DJVM by [com.r3.sgx.djvm.enclave.handlers.TaskHandler]
     */
    val userJars = mutableListOf<MemoryURL>()

    override fun send(needBytes: Int, serializers: MutableList<Consumer<ByteBuffer>>) {
        upstream.send(needBytes, serializers)
    }
}