package com.r3.sgx.enclavelethost.server.internal

import com.google.protobuf.ByteString
import com.r3.sgx.core.common.BytesHandler
import com.r3.sgx.core.common.MuxId
import com.r3.sgx.enclavelethost.grpc.*
import com.r3.sgx.enclavelethost.server.EnclaveletHost
import com.r3.sgx.enclavelethost.server.ExceptionListener
import io.grpc.Status
import io.grpc.stub.StreamObserver
import java.nio.ByteBuffer

class EnclaveletRpcImpl(
        val enclaveletState: EnclaveletState.Created,
        val exceptionListener: ExceptionListener
) : EnclaveletHostGrpc.EnclaveletHostImplBase() {

    override fun openSession(responseObserver: StreamObserver<ServerMessage>): StreamObserver<ClientMessage> {
        val enclaveHandler = EnclaveletOutputHandler(responseObserver)
        val (sessionId, enclaveSender) = enclaveletState.channels.addDownstream(enclaveHandler).get()
        return EnclaveletClientObserver(this, sessionId, enclaveSender, responseObserver)
    }

    override fun getEpidAttestation(request: GetEpidAttestationRequest, responseObserver: StreamObserver<GetEpidAttestationResponse>) {
        if (enclaveletState is EnclaveletState.Attested) {
            val response = enclaveletState.attestationResponse
            val attestation = EpidAttestation.newBuilder()
                    .setReport(ByteString.copyFrom(response.reportBytes))
                    .setSignature(ByteString.copyFrom(response.signature))
                    .setCertPath(ByteString.copyFrom(response.certPath.encoded))
                    .build()
            responseObserver.onNext(GetEpidAttestationResponse.newBuilder().setAttestation(attestation).build())
            responseObserver.onCompleted()
        } else {
                responseObserver.onError(Status.UNAVAILABLE.withDescription(
                        "Remote attestation not supported in simulation mode").asException())
        }
    }

    // Handler propagating Enclave output to client-side StreamObserver
    class EnclaveletOutputHandler(private val responseObserver: StreamObserver<ServerMessage>) : BytesHandler() {
        override fun onReceive(connection: Connection, input: ByteBuffer) {
            val message = ServerMessage.newBuilder().setBlob(ByteString.copyFrom(input)).build()
            responseObserver.onNext(message)
        }
    }

    // Observer processing messages sent by enclavelet client
    class EnclaveletClientObserver(
            val owner: EnclaveletRpcImpl,
            val sessionId: MuxId,
            val enclaveSender: BytesHandler.Connection,
            val responseObserver: StreamObserver<ServerMessage>
    ) : StreamObserver<ClientMessage> {

        override fun onNext(value: ClientMessage) {
            try {
                enclaveSender.send(value.blob.asReadOnlyByteBuffer())
            } catch (throwable: Throwable) {
                owner.exceptionListener.notifyException(throwable)
                throw throwable
            }
        }

        override fun onError(throwable: Throwable) {
            EnclaveletHost.log.info("In session-Id ${sessionId}, got error from client:", throwable)
            owner.enclaveletState.channels.removeDownstream(sessionId)
        }

        override fun onCompleted() {
            responseObserver.onCompleted()
            owner.enclaveletState.channels.removeDownstream(sessionId)
        }
    }


}