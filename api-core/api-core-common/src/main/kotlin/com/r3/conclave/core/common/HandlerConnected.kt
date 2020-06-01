package com.r3.conclave.core.common

import java.nio.ByteBuffer

class HandlerConnected<CONNECTION>(val handler: Handler<CONNECTION>, val connection: CONNECTION) {
    companion object {
        fun <CONNECTION> connect(handler: Handler<CONNECTION>, upstream: Sender): HandlerConnected<CONNECTION> {
            return HandlerConnected(handler, handler.connect(upstream))
        }
    }

    fun onReceive(input: ByteBuffer) {
        handler.onReceive(connection, input)
    }
}
