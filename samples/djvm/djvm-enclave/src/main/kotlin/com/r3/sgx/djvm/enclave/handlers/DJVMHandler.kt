package com.r3.sgx.djvm.enclave.handlers

import com.r3.sgx.core.common.Handler
import com.r3.sgx.core.common.Sender
import com.r3.sgx.djvm.enclave.connections.DJVMConnection
import com.r3.sgx.djvm.enclave.messages.MessageType.JAR
import com.r3.sgx.djvm.enclave.messages.MessageType.TASK
import java.nio.ByteBuffer

class DJVMHandler : Handler<DJVMConnection> {
    private val jarHandler : JarHandler = JarHandler()
    private val taskHandler : TaskHandler = TaskHandler()

    override fun onReceive(connection: DJVMConnection, input: ByteBuffer) {
        val messageType = input.int
        when (messageType) {
            JAR.ordinal -> jarHandler.onReceive(connection, input)
            TASK.ordinal -> taskHandler.onReceive(connection, input)
            else -> throw IllegalArgumentException("Unknown message type: $messageType")
        }
    }

    override fun connect(upstream: Sender) = DJVMConnection(upstream)
}