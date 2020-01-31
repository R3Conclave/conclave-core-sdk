package com.r3.conclave.sample.host

import com.r3.conclave.host.EnclaveHost
import com.r3.conclave.host.EnclaveMode

fun main(args: Array<String>) {
    val answer = callEnclave(args[0].toByteArray(), EnclaveMode.valueOf(args[1]))
    println(answer?.let(::String))
}

fun callEnclave(payload: ByteArray, mode: EnclaveMode): ByteArray? {
    val host = EnclaveHost.loadFromResources("com.r3.conclave.sample.enclave.ReverseEnclave", mode)
    host.start()
    return host.use {
        host.callEnclave(payload)
    }
}
