package com.r3.sgx.loadtest.sampler.rng

import com.r3.sgx.enclavelethost.grpc.EnclaveletHostGrpc
import com.r3.sgx.enclavelethost.grpc.GetEpidAttestationRequest
import com.r3.sgx.enclavelethost.grpc.GetEpidAttestationResponse
import org.apache.jmeter.protocol.java.sampler.JavaSamplerContext
import java.util.concurrent.ArrayBlockingQueue

class RngGetAttestationSampler : AbstractGrpcPoolingSampler() {
    override fun runTest(context: JavaSamplerContext, stub: EnclaveletHostGrpc.EnclaveletHostStub) {
        val queue = ArrayBlockingQueue<StreamMessage<GetEpidAttestationResponse>>(10)
        stub.getEpidAttestation(GetEpidAttestationRequest.getDefaultInstance(), QueuingStreamObserver(queue))
        queue.take().cast<StreamMessage.Next<GetEpidAttestationResponse>>()
        queue.take().cast<StreamMessage.Completed<GetEpidAttestationResponse>>()
    }
}
