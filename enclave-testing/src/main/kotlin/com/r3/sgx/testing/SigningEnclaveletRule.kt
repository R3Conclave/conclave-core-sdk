package com.r3.sgx.testing

import com.r3.sgx.core.common.Handler
import com.r3.sgx.enclavelethost.grpc.*
import io.grpc.ManagedChannel
import io.grpc.ManagedChannelBuilder
import io.grpc.stub.StreamObserver
import org.junit.rules.ExternalResource

class SigningEnclaveletRule(private val grpcPort: Int) : ExternalResource() {
    private lateinit var channel: ManagedChannel
    private lateinit var enclaveletHost: EnclaveletHostGrpc.EnclaveletHostStub

    override fun before() {
        channel = ManagedChannelBuilder.forTarget("localhost:$grpcPort")
            .usePlaintext()
            .build()
        enclaveletHost = EnclaveletHostGrpc.newStub(channel)
            .withCompression("gzip")
            .withWaitForReady()
    }

    override fun after() {
        if (::channel.isInitialized) {
            channel.shutdownNow()
        }
    }

    fun<CONNECTION> connectToHandler(handler: Handler<CONNECTION>): CONNECTION {
        return StreamObserverConnector(handler).connect(enclaveletHost)
    }

    fun getEpidAttestation(request: GetEpidAttestationRequest, responseObserver: StreamObserver<GetEpidAttestationResponse>) {
        enclaveletHost.getEpidAttestation(request, responseObserver)
    }
}