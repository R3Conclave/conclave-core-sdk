package com.r3.conclave.testing

import com.r3.conclave.core.common.Handler
import com.r3.conclave.core.common.Sender
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