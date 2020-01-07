package com.r3.sgx.djvm.handlers

import com.r3.sgx.core.common.Handler
import com.r3.sgx.core.common.Sender
import com.r3.sgx.djvm.enclave.messages.MessageType
import java.nio.ByteBuffer

class DJVMHandler : Handler<Sender> {
    private val userJarHandler = UserJarHandler()
    private val taskHandler = TaskHandler()

    override fun connect(upstream: Sender): Sender = upstream

    override fun onReceive(connection: Sender, input: ByteBuffer) {
        val messageType = input.int
        when (messageType) {
            MessageType.JAR.ordinal -> userJarHandler.onReceive(connection, input)
            MessageType.TASK.ordinal -> taskHandler.onReceive(connection, input)
            else -> throw IllegalArgumentException("Unknown message type: $messageType")
        }
    }
}