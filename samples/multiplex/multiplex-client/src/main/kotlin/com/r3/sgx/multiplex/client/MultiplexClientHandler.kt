@file:JvmName("MultiplexClient")
package com.r3.sgx.multiplex.client

import com.r3.sgx.core.common.*
import com.r3.sgx.multiplex.common.*
import java.nio.ByteBuffer
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import java.util.function.Consumer

class MultiplexClientHandler : Handler<MultiplexClientHandler.Connection> {
    private val muxingHandler = MuxingHandler()

    override fun connect(upstream: Sender): Connection {
        val root = muxingHandler.connect(upstream)
        return Connection(
            mux = root,
            loader = root.addDownstream(MultiplexDiscriminator.LOAD.id, LoadingHandler(root)),
            unloader = root.addDownstream(MultiplexDiscriminator.UNLOAD.id, UnloadingHandler(root))
        )
    }

    override fun onReceive(connection: Connection, input: ByteBuffer) {
        muxingHandler.onReceive(connection.mux, input)
    }

    class Connection internal constructor(
        val mux: MuxingHandler.Connection,
        val loader: LoadingConnection,
        val unloader: UnloadingConnection
    )
}

private class LoadingConnectionImpl(private val upstream: Sender) : LoadingConnection {
    private val connectionMap: MutableMap<Int, CompletableFuture<EnclaveConnection>> = ConcurrentHashMap()
    private val requestCounter = AtomicInteger(0)

    /**
     * Upload a dynamic enclave and request a connection for it.
     * @param jarData An enclave "fat jar".
     * @param jarHash The expected SHA-256 hash of [jarData].
     */
    override fun sendJar(jarData: ByteBuffer, jarHash: ByteBuffer): CompletableFuture<EnclaveConnection> {
        validateHash(jarHash)
        val future = CompletableFuture<EnclaveConnection>()
        val requestId = requestCounter.getAndIncrement()
        connectionMap[requestId] = future

        upstream.send(jarData.remaining() + SHA256_BYTES + Byte.SIZE_BYTES + Int.SIZE_BYTES, Consumer { buffer ->
            buffer.putInt(requestId)
            buffer.put(jarHash.duplicate())
            buffer.put(LoadEnclaveDiscriminator.CREATE.value)
            buffer.put(jarData.duplicate())
        })

        return future
    }

    /**
     * Request a connection for an existing dynamic enclave.
     * @param jarHash The SHA-256 of a previously loaded enclave "fat jar".
     */
    override fun useJar(jarHash: ByteBuffer): CompletableFuture<EnclaveConnection> {
        validateHash(jarHash)
        val future = CompletableFuture<EnclaveConnection>()
        val requestId = requestCounter.getAndIncrement()
        connectionMap[requestId] = future

        upstream.send(SHA256_BYTES + Byte.SIZE_BYTES + Int.SIZE_BYTES, Consumer { buffer ->
            buffer.putInt(requestId)
            buffer.put(jarHash.duplicate())
            buffer.put(LoadEnclaveDiscriminator.USE.value)
        })

        return future
    }

    fun complete(requestId: Int, enclaveConnection: EnclaveConnection) {
        connectionMap.remove(requestId)?.complete(enclaveConnection)
    }

    private fun validateHash(jarHash: ByteBuffer) {
        require(jarHash.remaining() == SHA256_BYTES) {
            "SHA-256 hash must contain 32 bytes."
        }
    }
}

private class LoadingHandler(private val dynamicRoot: MuxingHandler.Connection) : Handler<LoadingConnectionImpl> {
    override fun connect(upstream: Sender) = LoadingConnectionImpl(upstream)

    @Suppress("UsePropertyAccessSyntax")
    override fun onReceive(connection: LoadingConnectionImpl, input: ByteBuffer) {
        val requestId = input.getInt()
        val dynamicId = input.getInt()
        val enclaveHash = ByteArray(SHA256_BYTES).apply {
            input.get(this)
        }
        val downstream = dynamicRoot.getOrAddDownstream(dynamicId, DynamicEnclaveHandler(enclaveHash, dynamicId)).connection
        val enclaveConnection = downstream as? EnclaveConnection
            ?: throw IllegalArgumentException("Downstream connection of incorrect type: ${downstream!!::class.java.name}")
        connection.complete(requestId, enclaveConnection)
    }
}

private class UnloadingConnectionImpl(private val upstream: Sender) : UnloadingConnection {
    override fun unload(enclaveConnection: EnclaveConnection) {
        upstream.send(MuxId.SIZE_BYTES, Consumer {  buffer ->
            buffer.putInt(enclaveConnection.id)
        })
    }
}

private class UnloadingHandler(private val dynamicRoot: MuxingHandler.Connection) : Handler<UnloadingConnectionImpl> {
    override fun connect(upstream: Sender) = UnloadingConnectionImpl(upstream)

    @Suppress("UsePropertyAccessSyntax")
    override fun onReceive(connection: UnloadingConnectionImpl, input: ByteBuffer) {
        val dynamicId = input.getInt()
        dynamicRoot.removeDownstream(dynamicId)
    }
}

private class EnclaveConnectionImpl(
    private val enclaveHashBytes: ByteArray,
    override val id: MuxId,
    private val upstream: Sender
) : EnclaveConnection {
    private val enclaveHex = enclaveHashBytes.hashKey
    private lateinit var downstream: HandlerConnected<*>

    override val enclaveHash: ByteBuffer get() = ByteBuffer.wrap(enclaveHashBytes).asReadOnlyBuffer()

    @Synchronized
    override fun <CONNECTION> setDownstream(downstream: Handler<CONNECTION>): CONNECTION {
        if (this::downstream.isInitialized) {
            throw IllegalStateException("Can only have a single downstream")
        }

        val connection = downstream.connect(upstream)
        this.downstream = HandlerConnected(downstream, connection)
        return connection
    }

    override fun toString(): String = StringBuilder()
            .append("EnclaveConnection[Enclave=").append(enclaveHex)
            .append(", ID=").append(id)
            .append(']')
            .toString()

    fun onReceive(input: ByteBuffer) {
        downstream.onReceive(input)
    }
}

private class DynamicEnclaveHandler(private val enclaveHash: ByteArray, private val id: MuxId) : Handler<EnclaveConnectionImpl> {
    override fun connect(upstream: Sender): EnclaveConnectionImpl {
        return EnclaveConnectionImpl(enclaveHash, id, upstream)
    }

    override fun onReceive(connection: EnclaveConnectionImpl, input: ByteBuffer) {
        connection.onReceive(input)
    }
}
