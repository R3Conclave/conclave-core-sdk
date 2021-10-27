package com.r3.conclave.init.cli

import com.r3.conclave.init.ConclaveInit
import picocli.CommandLine
import kotlin.io.path.absolutePathString
import kotlin.io.path.exists
import kotlin.system.exitProcess

fun main(args: Array<String>) {
    val params = CommandLineParameters()
    val commandLine = CommandLine(params).setCaseInsensitiveEnumValuesAllowed(true)

    try {
        commandLine.parseArgs(*args)

        if (params.helpInfoRequested) commandLine.printHelpAndExit()
    } catch (e: CommandLine.PicocliException) {
        val message = (e.cause as? IllegalArgumentException)?.message ?: e.message
        commandLine.printErrorAndHelpAndExit(message)
    }

    with(params) {
        checkTargetDoesNotExist(this)

        println(projectSummary())
        ConclaveInit.createProject(language, basePackage, enclaveClass, target)
    }
}

private fun CommandLine.printHelpAndExit() {
    usage(System.out)
    exitProcess(0)
}

private fun CommandLine.printErrorAndHelpAndExit(message: String?) {
    println(message)
    println()
    usage(System.out)
    exitProcess(1)
}

private fun checkTargetDoesNotExist(params: CommandLineParameters) {
    if (params.target.exists()) {
        val message = "Target directory ${params.target.absolutePathString()} already exists. " +
                "Please delete the existing directory or specify a different target."
        println(message)
        exitProcess(1)
    }
}


