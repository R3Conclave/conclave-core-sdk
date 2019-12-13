package com.r3.sgx.testing

import com.r3.sgx.core.common.Sender
import com.r3.sgx.core.common.Handler
import java.nio.ByteBuffer
import java.util.function.Consumer

/**
 * A [Handler]/[Sender] pair that sends/receives strings.
 */
abstract class StringHandler : Handler<StringSender> {
    abstract fun onReceive(sender: StringSender, string: String)

    final override fun onReceive(connection: StringSender, input: ByteBuffer) {
        val bytes = ByteArray(input.remaining())
        input.get(bytes)
        onReceive(connection, String(bytes))
    }

    final override fun connect(upstream: Sender): StringSender {
        return StringSender(upstream)
    }
}

class StringSender(private val upstream: Sender) {
    fun send(string: String) {
        val bytes = string.toByteArray()
        upstream.send(bytes.size, Consumer { buffer ->
            buffer.put(bytes)
        })
    }
}
