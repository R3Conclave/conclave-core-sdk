package com.r3.conclave.threadingtest.host

import picocli.CommandLine
import picocli.CommandLine.Command
import kotlin.system.exitProcess

fun main(args: Array<String>) {
    exitProcess(CommandLine(Main()).execute(*args))
}

@Command(
    subcommands = [
        FibonacciHost::class,
        BusyHost::class
    ]
)

class Main : Runnable {
    override fun run() {
        println("Usage: <main class> [COMMAND]")
        println("Commands: fib, busy")
        exitProcess(1)
    }
}