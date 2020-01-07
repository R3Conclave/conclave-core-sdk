package com.r3.sgx.djvm.handlers

import com.r3.sgx.core.common.Handler
import com.r3.sgx.core.common.Sender
import org.assertj.core.api.Assertions.assertThat
import java.nio.ByteBuffer

class TaskHandler : Handler<Sender> {
    override fun connect(upstream: Sender): Sender = upstream

    override fun onReceive(connection: Sender, input: ByteBuffer) {
        val classNameSize = input.int
        val className = ByteArray(classNameSize)
        input.get(className)

        val responseSize = input.int
        val response = ByteArray(responseSize)
        input.get(response)

        when (String(className)) {
            "com.r3.sgx.djvm.KotlinTask" -> assertThat(String(response)).isEqualTo("Sandbox says: 'Hello World'")
            "com.r3.sgx.djvm.BadKotlinTask" -> assertThat(String(response)).isEqualTo("net.corda.djvm.execution.SandboxException: RuleViolationError: Disallowed reference to API; java.lang.Class.getField(String)")
            else -> throw IllegalStateException("Unknown class: " + String(className))
        }
    }
}