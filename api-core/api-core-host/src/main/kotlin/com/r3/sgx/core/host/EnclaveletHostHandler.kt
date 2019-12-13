package com.r3.sgx.core.host

import com.r3.sgx.core.common.*
import java.nio.ByteBuffer

/**
 * A handler capable of interfacing with [Enclavelet]s. It can be used to open channels to the enclave using
 * [Connection.channels], and retrieve a quote using [Connection.attestation].
 *
 * @param configuration attestation configuration.
 */
class EnclaveletHostHandler(
        configuration: EpidAttestationHostConfiguration
) : Handler<EnclaveletHostHandler.Connection> {
    private val errorHandler = ThrowingErrorHandler()
    private val muxingHandler = SimpleMuxingHandler()
    private val attestationHandler = EpidAttestationHostHandler(configuration)
    private val channelsHandler = ChannelInitiatingHandler()

    override fun connect(upstream: Sender): Connection {
        val errorConnection = errorHandler.connect(upstream)
        val muxConnection = errorConnection.addDownstream(muxingHandler)
        val channelsConnection = muxConnection.addDownstream(channelsHandler)
        val attestationConnection = muxConnection.addDownstream(attestationHandler)
        return Connection(errorConnection, muxConnection, attestationConnection, channelsConnection)
    }

    override fun onReceive(connection: Connection, input: ByteBuffer) {
        errorHandler.onReceive(connection.errors, input)
    }

    class Connection(
            val errors: ErrorHandler.Connection,
            val mux: SimpleMuxingHandler.Connection,
            val attestation: EpidAttestationHostHandler.Connection,
            val channels: ChannelInitiatingHandler.Connection
    )
}