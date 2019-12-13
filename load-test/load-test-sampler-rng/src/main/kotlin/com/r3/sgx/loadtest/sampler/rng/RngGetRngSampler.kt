package com.r3.sgx.loadtest.sampler.rng

import com.google.protobuf.ByteString
import com.r3.sgx.enclavelethost.grpc.ClientMessage
import com.r3.sgx.enclavelethost.grpc.EnclaveletHostGrpc
import com.r3.sgx.enclavelethost.grpc.ServerMessage
import org.apache.jmeter.protocol.java.sampler.JavaSamplerContext
import java.nio.ByteBuffer
import java.util.concurrent.ArrayBlockingQueue

class RngGetRngSampler : AbstractGrpcPoolingSampler() {
    override fun runTest(context: JavaSamplerContext, stub: EnclaveletHostGrpc.EnclaveletHostStub) {
        val queue = ArrayBlockingQueue<StreamMessage<ServerMessage>>(10)
        val session = stub.openSession(QueuingStreamObserver(queue))
        val buffer = ByteBuffer.allocate(4)
        buffer.putInt(1024)
        buffer.rewind()
        session.onNext(ClientMessage.newBuilder().setBlob(ByteString.copyFrom(buffer)).build())
        session.onCompleted()
        queue.take().cast<StreamMessage.Next<ServerMessage>>()
        queue.take().cast<StreamMessage.Completed<ServerMessage>>()
    }
}
