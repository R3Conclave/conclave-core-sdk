package com.r3.conclave.init.cli

import com.r3.conclave.init.ConclaveInit
import com.r3.conclave.init.common.walkTopDown
import picocli.CommandLine
import java.nio.file.Path
import java.util.jar.JarInputStream
import kotlin.io.path.absolutePathString
import kotlin.io.path.exists
import kotlin.io.path.extension
import kotlin.io.path.inputStream
import kotlin.system.exitProcess

fun main(args: Array<String>) {
    val params = CommandLineParameters()
    val commandLine = CommandLine(params)

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

        val conclaveVersion = getConclaveVersion(sdkRepo)

        println(projectSummary())
        ConclaveInit.createProject(language, basePackage, enclaveClass, target, sdkRepo, conclaveVersion)
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


private fun getConclaveVersion(sdkRepo: Path): String {
    val jarsFromRepo = sdkRepo.walkTopDown().filter { it.extension == "jar" }
    val conclaveVersion = jarsFromRepo.firstNotNullOfOrNull { jar ->
        val manifest = jar.inputStream().use { JarInputStream(it).manifest }
        manifest.mainAttributes.getValue("Conclave-Release-Version")
    }

    if (conclaveVersion == null) {
        System.err.println(
            "Error: could not detect Conclave version of repo $sdkRepo. Please add it manually " +
                    "to the gradle.properties file in the root of the generated project."
        )

        return ""
    }

    return conclaveVersion
}
