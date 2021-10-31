package com.r3.conclave.init.cli

import com.r3.conclave.init.ConclaveInit
import com.r3.conclave.init.template.ManifestFiles
import picocli.CommandLine
import java.io.IOException
import kotlin.io.path.absolutePathString
import kotlin.io.path.exists
import kotlin.system.exitProcess

fun main(args: Array<String>) {
    val params = CommandLineParameters()
    val commandLine = CommandLine(params).setCaseInsensitiveEnumValuesAllowed(true)

    try {
        commandLine.parseArgs(*args)
        if (params.helpInfoRequested) commandLine.printHelpAndExit()
        params.validateSdkRepo()
    } catch (e: IllegalArgumentException) {
        commandLine.printErrorAndHelpAndExit(e.message)
    } catch (e: CommandLine.PicocliException) {
        val message = (e.cause as? IllegalArgumentException)?.message ?: e.message
        commandLine.printErrorAndHelpAndExit(message)
    }

    with(params) {
        checkTargetDoesNotExist(this)

        println(projectSummary())
        ConclaveInit.createProject(language, basePackage, enclaveClass, target, sdkRepo, getConclaveVersion())
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

private fun getConclaveVersion(): String = try {
    ManifestFiles.load().conclaveVersion
} catch (e: IOException) {
    System.err.println(
        "Error: could not detect ConclaveVersion. " +
                "Please add it manually to gradle.properties of the generated project"
    )
    returnEmptyString()
}

private fun returnEmptyString(): String = ""