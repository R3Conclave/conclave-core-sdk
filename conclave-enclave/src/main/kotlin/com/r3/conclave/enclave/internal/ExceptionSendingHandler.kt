package com.r3.conclave.enclave.internal

import com.r3.conclave.common.internal.SerializeException
import com.r3.conclave.common.internal.handler.Handler
import com.r3.conclave.common.internal.handler.HandlerConnected
import com.r3.conclave.common.internal.handler.Sender
import com.r3.conclave.mail.MailDecryptionException
import java.nio.ByteBuffer
import java.util.function.Consumer

/**
 * A [Handler] that handles errors from either side.
 *
 * If a downstream raises an exception it will be serialized and sent to the other side.
 *
 * @param isReleaseMode If the enclave is in release mode then exceptions are not exposed.
 */
class ExceptionSendingHandler(private val isReleaseMode: Boolean) : Handler<ExceptionSendingHandler.Connection> {
    override fun onReceive(connection: Connection, input: ByteBuffer) {
        try {
            val downstream = checkNotNull(connection.getDownstream()) { "No downstream specified" }
            downstream.onReceive(input)
        } catch (throwable: Throwable) {
            val exceptionToSerialize = if (isReleaseMode) {
                if (throwable is MailDecryptionException) {
                    // Release enclaves still need to notify the host if they were unable to decrypt mail, but there's
                    // no need to include the message or stack trace in case any secrets can be inferred from them.
                    MailDecryptionException()
                } else {
                    RuntimeException("Release enclave threw an exception which was swallowed to avoid leaking any " +
                            "secrets")
                }
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
            val serialised = SerializeException.serialise(throwable)
            errorSender.send(serialised.size) { buffer ->
                buffer.put(serialised)
            }
        }

        @Synchronized
        fun <CONNECTION> setDownstream(downstream: Handler<CONNECTION>): CONNECTION {
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
    ) : Sender {
        override fun send(needBytes: Int, serializers: MutableList<Consumer<ByteBuffer>>) {
            serializers.add(Consumer { buffer ->
                buffer.put(discriminator.value)
            })
            upstream.send(needBytes + Byte.SIZE_BYTES, serializers)
        }
    }
}
