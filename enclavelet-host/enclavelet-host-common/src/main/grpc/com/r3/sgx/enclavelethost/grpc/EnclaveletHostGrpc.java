package com.r3.sgx.enclavelethost.grpc;

import static io.grpc.MethodDescriptor.generateFullMethodName;
import static io.grpc.stub.ClientCalls.asyncBidiStreamingCall;
import static io.grpc.stub.ClientCalls.asyncClientStreamingCall;
import static io.grpc.stub.ClientCalls.asyncServerStreamingCall;
import static io.grpc.stub.ClientCalls.asyncUnaryCall;
import static io.grpc.stub.ClientCalls.blockingServerStreamingCall;
import static io.grpc.stub.ClientCalls.blockingUnaryCall;
import static io.grpc.stub.ClientCalls.futureUnaryCall;
import static io.grpc.stub.ServerCalls.asyncBidiStreamingCall;
import static io.grpc.stub.ServerCalls.asyncClientStreamingCall;
import static io.grpc.stub.ServerCalls.asyncServerStreamingCall;
import static io.grpc.stub.ServerCalls.asyncUnaryCall;
import static io.grpc.stub.ServerCalls.asyncUnimplementedStreamingCall;
import static io.grpc.stub.ServerCalls.asyncUnimplementedUnaryCall;

/**
 */
@javax.annotation.Generated(
    value = "by gRPC proto compiler (version 1.13.2)",
    comments = "Source: enclavelet-host.proto")
public final class EnclaveletHostGrpc {

  private EnclaveletHostGrpc() {}

  public static final String SERVICE_NAME = "proto.EnclaveletHost";

  // Static method descriptors that strictly reflect the proto.
  private static volatile io.grpc.MethodDescriptor<com.r3.sgx.enclavelethost.grpc.GetEpidAttestationRequest,
      com.r3.sgx.enclavelethost.grpc.GetEpidAttestationResponse> getGetEpidAttestationMethod;

  public static io.grpc.MethodDescriptor<com.r3.sgx.enclavelethost.grpc.GetEpidAttestationRequest,
      com.r3.sgx.enclavelethost.grpc.GetEpidAttestationResponse> getGetEpidAttestationMethod() {
    io.grpc.MethodDescriptor<com.r3.sgx.enclavelethost.grpc.GetEpidAttestationRequest, com.r3.sgx.enclavelethost.grpc.GetEpidAttestationResponse> getGetEpidAttestationMethod;
    if ((getGetEpidAttestationMethod = EnclaveletHostGrpc.getGetEpidAttestationMethod) == null) {
      synchronized (EnclaveletHostGrpc.class) {
        if ((getGetEpidAttestationMethod = EnclaveletHostGrpc.getGetEpidAttestationMethod) == null) {
          EnclaveletHostGrpc.getGetEpidAttestationMethod = getGetEpidAttestationMethod = 
              io.grpc.MethodDescriptor.<com.r3.sgx.enclavelethost.grpc.GetEpidAttestationRequest, com.r3.sgx.enclavelethost.grpc.GetEpidAttestationResponse>newBuilder()
              .setType(io.grpc.MethodDescriptor.MethodType.UNARY)
              .setFullMethodName(generateFullMethodName(
                  "proto.EnclaveletHost", "GetEpidAttestation"))
              .setSampledToLocalTracing(true)
              .setRequestMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  com.r3.sgx.enclavelethost.grpc.GetEpidAttestationRequest.getDefaultInstance()))
              .setResponseMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  com.r3.sgx.enclavelethost.grpc.GetEpidAttestationResponse.getDefaultInstance()))
                  .setSchemaDescriptor(new EnclaveletHostMethodDescriptorSupplier("GetEpidAttestation"))
                  .build();
          }
        }
     }
     return getGetEpidAttestationMethod;
  }

  private static volatile io.grpc.MethodDescriptor<com.r3.sgx.enclavelethost.grpc.ClientMessage,
      com.r3.sgx.enclavelethost.grpc.ServerMessage> getOpenSessionMethod;

  public static io.grpc.MethodDescriptor<com.r3.sgx.enclavelethost.grpc.ClientMessage,
      com.r3.sgx.enclavelethost.grpc.ServerMessage> getOpenSessionMethod() {
    io.grpc.MethodDescriptor<com.r3.sgx.enclavelethost.grpc.ClientMessage, com.r3.sgx.enclavelethost.grpc.ServerMessage> getOpenSessionMethod;
    if ((getOpenSessionMethod = EnclaveletHostGrpc.getOpenSessionMethod) == null) {
      synchronized (EnclaveletHostGrpc.class) {
        if ((getOpenSessionMethod = EnclaveletHostGrpc.getOpenSessionMethod) == null) {
          EnclaveletHostGrpc.getOpenSessionMethod = getOpenSessionMethod = 
              io.grpc.MethodDescriptor.<com.r3.sgx.enclavelethost.grpc.ClientMessage, com.r3.sgx.enclavelethost.grpc.ServerMessage>newBuilder()
              .setType(io.grpc.MethodDescriptor.MethodType.BIDI_STREAMING)
              .setFullMethodName(generateFullMethodName(
                  "proto.EnclaveletHost", "OpenSession"))
              .setSampledToLocalTracing(true)
              .setRequestMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  com.r3.sgx.enclavelethost.grpc.ClientMessage.getDefaultInstance()))
              .setResponseMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  com.r3.sgx.enclavelethost.grpc.ServerMessage.getDefaultInstance()))
                  .setSchemaDescriptor(new EnclaveletHostMethodDescriptorSupplier("OpenSession"))
                  .build();
          }
        }
     }
     return getOpenSessionMethod;
  }

  /**
   * Creates a new async stub that supports all call types for the service
   */
  public static EnclaveletHostStub newStub(io.grpc.Channel channel) {
    return new EnclaveletHostStub(channel);
  }

  /**
   * Creates a new blocking-style stub that supports unary and streaming output calls on the service
   */
  public static EnclaveletHostBlockingStub newBlockingStub(
      io.grpc.Channel channel) {
    return new EnclaveletHostBlockingStub(channel);
  }

  /**
   * Creates a new ListenableFuture-style stub that supports unary calls on the service
   */
  public static EnclaveletHostFutureStub newFutureStub(
      io.grpc.Channel channel) {
    return new EnclaveletHostFutureStub(channel);
  }

  /**
   */
  public static abstract class EnclaveletHostImplBase implements io.grpc.BindableService {

    /**
     * <pre>
     * Request a signed quote of the enclave instance.
     * </pre>
     */
    public void getEpidAttestation(com.r3.sgx.enclavelethost.grpc.GetEpidAttestationRequest request,
        io.grpc.stub.StreamObserver<com.r3.sgx.enclavelethost.grpc.GetEpidAttestationResponse> responseObserver) {
      asyncUnimplementedUnaryCall(getGetEpidAttestationMethod(), responseObserver);
    }

    /**
     * <pre>
     * Establish a session with an enclave.
     * </pre>
     */
    public io.grpc.stub.StreamObserver<com.r3.sgx.enclavelethost.grpc.ClientMessage> openSession(
        io.grpc.stub.StreamObserver<com.r3.sgx.enclavelethost.grpc.ServerMessage> responseObserver) {
      return asyncUnimplementedStreamingCall(getOpenSessionMethod(), responseObserver);
    }

    @java.lang.Override public final io.grpc.ServerServiceDefinition bindService() {
      return io.grpc.ServerServiceDefinition.builder(getServiceDescriptor())
          .addMethod(
            getGetEpidAttestationMethod(),
            asyncUnaryCall(
              new MethodHandlers<
                com.r3.sgx.enclavelethost.grpc.GetEpidAttestationRequest,
                com.r3.sgx.enclavelethost.grpc.GetEpidAttestationResponse>(
                  this, METHODID_GET_EPID_ATTESTATION)))
          .addMethod(
            getOpenSessionMethod(),
            asyncBidiStreamingCall(
              new MethodHandlers<
                com.r3.sgx.enclavelethost.grpc.ClientMessage,
                com.r3.sgx.enclavelethost.grpc.ServerMessage>(
                  this, METHODID_OPEN_SESSION)))
          .build();
    }
  }

  /**
   */
  public static final class EnclaveletHostStub extends io.grpc.stub.AbstractStub<EnclaveletHostStub> {
    private EnclaveletHostStub(io.grpc.Channel channel) {
      super(channel);
    }

    private EnclaveletHostStub(io.grpc.Channel channel,
        io.grpc.CallOptions callOptions) {
      super(channel, callOptions);
    }

    @java.lang.Override
    protected EnclaveletHostStub build(io.grpc.Channel channel,
        io.grpc.CallOptions callOptions) {
      return new EnclaveletHostStub(channel, callOptions);
    }

    /**
     * <pre>
     * Request a signed quote of the enclave instance.
     * </pre>
     */
    public void getEpidAttestation(com.r3.sgx.enclavelethost.grpc.GetEpidAttestationRequest request,
        io.grpc.stub.StreamObserver<com.r3.sgx.enclavelethost.grpc.GetEpidAttestationResponse> responseObserver) {
      asyncUnaryCall(
          getChannel().newCall(getGetEpidAttestationMethod(), getCallOptions()), request, responseObserver);
    }

    /**
     * <pre>
     * Establish a session with an enclave.
     * </pre>
     */
    public io.grpc.stub.StreamObserver<com.r3.sgx.enclavelethost.grpc.ClientMessage> openSession(
        io.grpc.stub.StreamObserver<com.r3.sgx.enclavelethost.grpc.ServerMessage> responseObserver) {
      return asyncBidiStreamingCall(
          getChannel().newCall(getOpenSessionMethod(), getCallOptions()), responseObserver);
    }
  }

  /**
   */
  public static final class EnclaveletHostBlockingStub extends io.grpc.stub.AbstractStub<EnclaveletHostBlockingStub> {
    private EnclaveletHostBlockingStub(io.grpc.Channel channel) {
      super(channel);
    }

    private EnclaveletHostBlockingStub(io.grpc.Channel channel,
        io.grpc.CallOptions callOptions) {
      super(channel, callOptions);
    }

    @java.lang.Override
    protected EnclaveletHostBlockingStub build(io.grpc.Channel channel,
        io.grpc.CallOptions callOptions) {
      return new EnclaveletHostBlockingStub(channel, callOptions);
    }

    /**
     * <pre>
     * Request a signed quote of the enclave instance.
     * </pre>
     */
    public com.r3.sgx.enclavelethost.grpc.GetEpidAttestationResponse getEpidAttestation(com.r3.sgx.enclavelethost.grpc.GetEpidAttestationRequest request) {
      return blockingUnaryCall(
          getChannel(), getGetEpidAttestationMethod(), getCallOptions(), request);
    }
  }

  /**
   */
  public static final class EnclaveletHostFutureStub extends io.grpc.stub.AbstractStub<EnclaveletHostFutureStub> {
    private EnclaveletHostFutureStub(io.grpc.Channel channel) {
      super(channel);
    }

    private EnclaveletHostFutureStub(io.grpc.Channel channel,
        io.grpc.CallOptions callOptions) {
      super(channel, callOptions);
    }

    @java.lang.Override
    protected EnclaveletHostFutureStub build(io.grpc.Channel channel,
        io.grpc.CallOptions callOptions) {
      return new EnclaveletHostFutureStub(channel, callOptions);
    }

    /**
     * <pre>
     * Request a signed quote of the enclave instance.
     * </pre>
     */
    public com.google.common.util.concurrent.ListenableFuture<com.r3.sgx.enclavelethost.grpc.GetEpidAttestationResponse> getEpidAttestation(
        com.r3.sgx.enclavelethost.grpc.GetEpidAttestationRequest request) {
      return futureUnaryCall(
          getChannel().newCall(getGetEpidAttestationMethod(), getCallOptions()), request);
    }
  }

  private static final int METHODID_GET_EPID_ATTESTATION = 0;
  private static final int METHODID_OPEN_SESSION = 1;

  private static final class MethodHandlers<Req, Resp> implements
      io.grpc.stub.ServerCalls.UnaryMethod<Req, Resp>,
      io.grpc.stub.ServerCalls.ServerStreamingMethod<Req, Resp>,
      io.grpc.stub.ServerCalls.ClientStreamingMethod<Req, Resp>,
      io.grpc.stub.ServerCalls.BidiStreamingMethod<Req, Resp> {
    private final EnclaveletHostImplBase serviceImpl;
    private final int methodId;

    MethodHandlers(EnclaveletHostImplBase serviceImpl, int methodId) {
      this.serviceImpl = serviceImpl;
      this.methodId = methodId;
    }

    @java.lang.Override
    @java.lang.SuppressWarnings("unchecked")
    public void invoke(Req request, io.grpc.stub.StreamObserver<Resp> responseObserver) {
      switch (methodId) {
        case METHODID_GET_EPID_ATTESTATION:
          serviceImpl.getEpidAttestation((com.r3.sgx.enclavelethost.grpc.GetEpidAttestationRequest) request,
              (io.grpc.stub.StreamObserver<com.r3.sgx.enclavelethost.grpc.GetEpidAttestationResponse>) responseObserver);
          break;
        default:
          throw new AssertionError();
      }
    }

    @java.lang.Override
    @java.lang.SuppressWarnings("unchecked")
    public io.grpc.stub.StreamObserver<Req> invoke(
        io.grpc.stub.StreamObserver<Resp> responseObserver) {
      switch (methodId) {
        case METHODID_OPEN_SESSION:
          return (io.grpc.stub.StreamObserver<Req>) serviceImpl.openSession(
              (io.grpc.stub.StreamObserver<com.r3.sgx.enclavelethost.grpc.ServerMessage>) responseObserver);
        default:
          throw new AssertionError();
      }
    }
  }

  private static abstract class EnclaveletHostBaseDescriptorSupplier
      implements io.grpc.protobuf.ProtoFileDescriptorSupplier, io.grpc.protobuf.ProtoServiceDescriptorSupplier {
    EnclaveletHostBaseDescriptorSupplier() {}

    @java.lang.Override
    public com.google.protobuf.Descriptors.FileDescriptor getFileDescriptor() {
      return com.r3.sgx.enclavelethost.grpc.EnclaveletHostApi.getDescriptor();
    }

    @java.lang.Override
    public com.google.protobuf.Descriptors.ServiceDescriptor getServiceDescriptor() {
      return getFileDescriptor().findServiceByName("EnclaveletHost");
    }
  }

  private static final class EnclaveletHostFileDescriptorSupplier
      extends EnclaveletHostBaseDescriptorSupplier {
    EnclaveletHostFileDescriptorSupplier() {}
  }

  private static final class EnclaveletHostMethodDescriptorSupplier
      extends EnclaveletHostBaseDescriptorSupplier
      implements io.grpc.protobuf.ProtoMethodDescriptorSupplier {
    private final String methodName;

    EnclaveletHostMethodDescriptorSupplier(String methodName) {
      this.methodName = methodName;
    }

    @java.lang.Override
    public com.google.protobuf.Descriptors.MethodDescriptor getMethodDescriptor() {
      return getServiceDescriptor().findMethodByName(methodName);
    }
  }

  private static volatile io.grpc.ServiceDescriptor serviceDescriptor;

  public static io.grpc.ServiceDescriptor getServiceDescriptor() {
    io.grpc.ServiceDescriptor result = serviceDescriptor;
    if (result == null) {
      synchronized (EnclaveletHostGrpc.class) {
        result = serviceDescriptor;
        if (result == null) {
          serviceDescriptor = result = io.grpc.ServiceDescriptor.newBuilder(SERVICE_NAME)
              .setSchemaDescriptor(new EnclaveletHostFileDescriptorSupplier())
              .addMethod(getGetEpidAttestationMethod())
              .addMethod(getOpenSessionMethod())
              .build();
        }
      }
    }
    return result;
  }
}
