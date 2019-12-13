package com.r3.sgx.core.common

import com.google.protobuf.CodedOutputStream
import java.nio.ByteBuffer
import java.util.function.Consumer

/**
 * A [Handler] that handles errors from either side.
 *
 * If a downstream raises an exception it will be serialized and sent to the other side.
 *
 * @param exposeErrors if true the sent errors will be exposed, if false they will be replaced with an opaque one,
 *     hiding the original error condition.
 */
class ExceptionSendingHandler(private val exposeErrors: Boolean): Handler<ExceptionSendingHandler.Connection> {

    override fun onReceive(connection: Connection, input: ByteBuffer) {
        try {
            val downstream = connection.getDownstream()
                    ?: throw IllegalStateException("No downstream specified")
            downstream.onReceive(input)
        } catch (throwable: Throwable) {
            val exceptionToSerialize = if (!exposeErrors) {
                Exception(
                        "Opaque error. If this error is coming from a Release enclave you can use a Simulation or " +
                                "Debug enclave to reveal the full error."
                )
            } else {
                throwable
            }
            connection.sendException(exceptionToSerialize)
        }
    }

    override fun connect(upstream: Sender): Connection {
        return Connection(upstream)
    }

    inner class Connection(upstream: Sender) {
        private val noErrorSender: Sender = ErrorReportingSender(SerializeException.Discriminator.NO_ERROR, upstream)
        private val errorSender: Sender = ErrorReportingSender(SerializeException.Discriminator.ERROR, upstream)

        private var downstream: HandlerConnected<*>? = null

        fun getDownstream(): HandlerConnected<*>? {
            return downstream
        }

        fun sendException(throwable: Throwable) {
            val exception = SerializeException.exceptionFromThrowable(throwable)
            errorSender.send(exception.serializedSize, Consumer { buffer ->
                val output = CodedOutputStream.newInstance(buffer)
                exception.writeTo(output)
                output.flush()
            })
        }

        @Synchronized
        fun <CONNECTION> addDownstream(downstream: Handler<CONNECTION>): CONNECTION {
            if (this.downstream != null) {
                throw IllegalArgumentException("Can only have a single downstream")
            } else {
                val connection = downstream.connect(noErrorSender)
                this.downstream = HandlerConnected(downstream, connection)
                return connection
            }
        }
    }

    private inner class ErrorReportingSender(
            val discriminator: SerializeException.Discriminator,
            val upstream: Sender
    ): Sender {
        override fun send(needBytes: Int, serializers: MutableList<Consumer<ByteBuffer>>) {
            serializers.add(Consumer { buffer ->
                buffer.put(discriminator.value)
            })
            upstream.send(needBytes + Byte.SIZE_BYTES, serializers)
        }
    }
}
