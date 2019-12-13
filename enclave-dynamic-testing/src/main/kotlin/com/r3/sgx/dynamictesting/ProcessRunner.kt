package com.r3.sgx.dynamictesting

import java.io.File

object ProcessRunner {
    fun runProcess(commandLine: List<String>, directory: File) {
        println("Running ${commandLine.joinToString(" ")}")
        val exitCode =
                ProcessBuilder(commandLine)
                        .inheritIO()
                        .directory(directory)
                        .start()
                        .waitFor()
        if (exitCode != 0) {
            throw IllegalStateException("Command '${commandLine.joinToString(" ")}' failed with exit code $exitCode")
        }
    }
}