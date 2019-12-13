package com.r3.sgx.testing

import com.google.protobuf.ByteString
import com.r3.sgx.core.common.Handler
import com.r3.sgx.core.common.LeafSender
import com.r3.sgx.enclavelethost.grpc.ClientMessage
import com.r3.sgx.enclavelethost.grpc.EnclaveletHostGrpc
import com.r3.sgx.enclavelethost.grpc.ServerMessage
import io.grpc.stub.StreamObserver
import java.nio.ByteBuffer
import java.util.concurrent.CompletableFuture

class StreamObserverConnector<CONNECTION>(private val downstream: Handler<CONNECTION>) {
    fun connect(rpcProxy: EnclaveletHostGrpc.EnclaveletHostStub) : CONNECTION {
        val connection = CompletableFuture<CONNECTION>()
        val clientStream = rpcProxy.openSession(
                object : StreamObserver<ServerMessage> {
                    override fun onNext(value: ServerMessage) {
                        downstream.onReceive(connection.get(), value.blob.asReadOnlyByteBuffer())
                    }

                    override fun onCompleted() {
                    }

                    override fun onError(t: Throwable) {
                        throw t
                    }
                })
        connection.complete(downstream.connect(Sender(clientStream)))
        return connection.get()
    }

    class Sender(val stream: StreamObserver<ClientMessage>): LeafSender()  {
        override fun sendSerialized(serializedBuffer: ByteBuffer) {
            stream.onNext(ClientMessage.newBuilder()
                    .setBlob(ByteString.copyFrom(serializedBuffer))
                    .build())
        }
    }
}