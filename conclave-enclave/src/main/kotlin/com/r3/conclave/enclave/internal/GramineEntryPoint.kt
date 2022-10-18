package com.r3.conclave.enclave.internal

class GramineEntryPoint {

    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            println("Gramine java 'enclave' started.")
            Thread.sleep(10000)
        }
    }
}
