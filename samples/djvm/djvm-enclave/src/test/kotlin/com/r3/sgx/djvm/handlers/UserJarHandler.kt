package com.r3.sgx.djvm.handlers

import com.r3.sgx.core.common.Handler
import com.r3.sgx.core.common.Sender
import com.r3.sgx.djvm.enclave.messages.Status
import java.nio.ByteBuffer

class UserJarHandler : Handler<Sender> {
    override fun connect(upstream: Sender): Sender = upstream

    override fun onReceive(connection: Sender, input: ByteBuffer) {
        val response = input.int
        when (response) {
            Status.FAIL.ordinal -> throw IllegalStateException("Jar upload failure!")
        }
    }
}