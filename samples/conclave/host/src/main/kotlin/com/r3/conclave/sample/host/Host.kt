package com.r3.conclave.sample.host

import com.r3.conclave.host.EnclaveHost
import com.r3.conclave.host.EnclaveMode
import java.nio.file.Path
import java.nio.file.Paths

fun main(args: Array<String>) {
    val answer = callEnclave(Paths.get(args[0]), args[1].toByteArray())
    println(answer?.let(::String))
}

fun callEnclave(enclaveFile: Path, payload: ByteArray): ByteArray? {
    val host = EnclaveHost.loadFromDisk(enclaveFile, EnclaveMode.SIMULATION)
    host.start()
    return host.use {
        host.callEnclave(payload)
    }
}
