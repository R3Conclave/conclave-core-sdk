package com.r3.conclave.sample.host

import com.r3.conclave.host.EnclaveHost

fun main(args: Array<String>) {
    val answer = callEnclave(args[0].toByteArray())
    println(answer?.let(::String))
}

fun callEnclave(payload: ByteArray): ByteArray? {
    val host = EnclaveHost.load("com.r3.conclave.sample.enclave.ReverseEnclave")
    host.start()
    return host.use {
        host.callEnclave(payload)
    }
}
