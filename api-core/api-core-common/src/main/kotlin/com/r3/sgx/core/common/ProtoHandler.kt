package com.r3.sgx.core.common

import com.google.protobuf.CodedInputStream
import com.google.protobuf.GeneratedMessageV3
import com.google.protobuf.Parser
import java.nio.ByteBuffer
import java.util.*

/**
 * A type-safe protobuf [Handler]. It uses an eventloop to prevent indefinite nesting of ecalls-ocalls
 * @param IN the incoming message type.
 * @param OUT the outgoing message type.
 */
abstract class ProtoHandler<IN, OUT : GeneratedMessageV3, CONNECTION>(private val parser: Parser<IN>) : Handler<CONNECTION> {
    /**
     * The receiver to be implemented by concrete subclasses.
     */
    abstract fun onReceive(connection: CONNECTION, message: IN)

    abstract fun connect(upstream: ProtoSender<OUT>): CONNECTION

    private var inEventLoop = false
    private var queue = ArrayDeque<IN>()
    final override fun onReceive(connection: CONNECTION, input: ByteBuffer) {
        val message = parser.parseFrom(CodedInputStream.newInstance(input))
        synchronized(this) {
            queue.addLast(message)
            if (inEventLoop) {
                return
            } else {
                inEventLoop = true
            }
        }
        try {
            while (true) {
                val next = synchronized(this) {
                    queue.pollFirst() ?: return
                }
                onReceive(connection, next)
            }
        } finally {
            inEventLoop = false
        }
    }

    final override fun connect(upstream: Sender): CONNECTION {
        return connect(ProtoSender(upstream))
    }
}