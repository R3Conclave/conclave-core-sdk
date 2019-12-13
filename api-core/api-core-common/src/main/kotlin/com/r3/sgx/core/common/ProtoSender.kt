package com.r3.sgx.core.common

import com.google.protobuf.CodedOutputStream
import com.google.protobuf.GeneratedMessageV3
import java.util.function.Consumer

/**
 * A type-safe protobuf [Sender]. It simply serializes messages to the byte buffer.
 * @param [OUT] the outgoing message type.
 */
class ProtoSender<OUT : GeneratedMessageV3>(private val upstream: Sender) {
    fun send(message: OUT) {
        upstream.send(message.serializedSize, Consumer { buffer ->
            val output = CodedOutputStream.newInstance(buffer)
            message.writeTo(output)
            output.flush()
        })
    }
}