package com.r3.sgx.testing

import com.r3.sgx.core.common.Handler
import com.r3.sgx.core.common.Sender
import java.nio.ByteBuffer
import java.util.function.Consumer

class EchoHandler : Handler<Sender> {
    override fun onReceive(connection: Sender, input: ByteBuffer) {
        connection.send(input.remaining(), Consumer { buffer ->
            buffer.put(input)
        })
    }

    override fun connect(upstream: Sender) = upstream
}