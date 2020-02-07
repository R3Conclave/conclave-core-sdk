package com.r3.sgx.multiplex.enclave

import com.r3.sgx.core.common.*
import com.r3.sgx.core.enclave.EnclaveApi
import com.r3.sgx.multiplex.common.*
import java.lang.IllegalStateException
import java.nio.ByteBuffer
import java.util.concurrent.atomic.AtomicInteger
import java.util.function.Consumer

class MultiplexEnclaveHandler internal constructor(private val api: EnclaveApi): Handler<MuxingHandler.Connection> {
    private val muxingHandler = MuxingHandler()
    private val muxIdCounter = AtomicInteger(0)
    private val jarCache = EnclaveJarCache()

    override fun connect(upstream: Sender): MuxingHandler.Connection {
        return muxingHandler.connect(upstream).also { root ->
            root.addDownstream(MultiplexDiscriminator.LOAD.id, LoadingHandler(root))
            root.addDownstream(MultiplexDiscriminator.UNLOAD.id, UnloadingHandler(root))
        }
    }

    override fun onReceive(connection: MuxingHandler.Connection, input: ByteBuffer) {
        muxingHandler.onReceive(connection, input)
    }

    /**
     * Creates a dynamic enclave by loading its JAR into memory.
     */
    private inner class LoadingHandler(private val dynamicRoot: MuxingHandler.Connection) : Handler<Sender> {
        override fun connect(upstream: Sender) = upstream

        @Suppress("UsePropertyAccessSyntax")
        override fun onReceive(connection: Sender, input: ByteBuffer) {
            val requestId = input.getInt()
            val jarHash = ByteArray(SHA256_BYTES).apply {
                input.get(this)
            }

            val dynamicEnclave = when (val discriminator = input.get()) {
                LoadEnclaveDiscriminator.CREATE.value -> {
                    val jarData = input.slice()
                    jarCache.createOrGet(jarData, expectedKey = jarHash.hashKey)
                }
                LoadEnclaveDiscriminator.USE.value -> jarCache[jarHash.hashKey]
                else -> throw IllegalStateException("Unknown LoadEnclaveDiscriminator '$discriminator'")
            }

            val dynamicId = muxIdCounter.getAndIncrement()
            dynamicRoot.addDownstream(dynamicId, DynamicEnclaveHandler(dynamicEnclave, api))
            connection.send(Int.SIZE_BYTES + MuxId.SIZE_BYTES + SHA256_BYTES, Consumer { buffer ->
                buffer.putInt(requestId)
                buffer.putInt(dynamicId)
                buffer.put(jarHash)
            })
        }
    }
}

/**
 * Removes the given dynamic enclave, preventing it receiving any further requests.
 * The JAR's memory will not be released until its [MemoryClassLoader] has also been
 * garbage collected.
 */
private class UnloadingHandler(private val dynamicRoot: MuxingHandler.Connection): Handler<Sender> {
    override fun connect(upstream: Sender)= upstream

    @Suppress("UsePropertyAccessSyntax")
    override fun onReceive(connection: Sender, input: ByteBuffer) {
        val dynamicId: MuxId = input.getInt()
        dynamicRoot.removeDownstream(dynamicId) ?: throw IllegalStateException("No dynamic enclave '$dynamicId'")
        connection.send(MuxId.SIZE_BYTES, Consumer { buffer ->
            buffer.putInt(dynamicId)
        })
    }
}

/**
 * Creates a handler for a new instance of this dynamic enclave.
 */
private class DynamicEnclaveHandler(private val enclaveJar: EnclaveJar, private val api: EnclaveApi): Handler<DynamicEnclaveApi> {
    override fun connect(upstream: Sender) = DynamicEnclaveApi(upstream, enclaveJar, api)

    override fun onReceive(connection: DynamicEnclaveApi, input: ByteBuffer) {
        connection.onReceive(input)
    }
}
