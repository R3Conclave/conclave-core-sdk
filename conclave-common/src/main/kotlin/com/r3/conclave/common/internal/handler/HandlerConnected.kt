package com.r3.conclave.common.internal.handler

import java.nio.ByteBuffer

class HandlerConnected<CONNECTION>(val handler: Handler<CONNECTION>, val connection: CONNECTION) {
    companion object {
        fun <CONNECTION> connect(handler: Handler<CONNECTION>, upstream: Sender): HandlerConnected<CONNECTION> {
            return HandlerConnected(handler, handler.connect(upstream))
        }
    }

    /**
     * @see Handler.onReceive
     */
    fun onReceive(input: ByteBuffer) {
        handler.onReceive(connection, input)
    }
}
