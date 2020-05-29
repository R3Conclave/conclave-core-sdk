package com.r3.conclave.loadtest.runner

import org.apache.jmeter.NewDriver
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import picocli.CommandLine
import java.io.File
import java.util.concurrent.Callable

@CommandLine.Command(
        description = ["Runs jmeter in headless mode"],
        mixinStandardHelpOptions = true
)
class JmeterRunnerCli : Callable<Unit> {
    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            CommandLine(JmeterRunnerCli()).execute(*args)
        }

        val log: Logger = LoggerFactory.getLogger(JmeterRunnerCli::class.java)
    }

    @CommandLine.Option(
            names = ["--sampler"],
            description = [
                "The sampler) to use to execute the given test plan",
                "This may be a jar or a directory containing jars"
            ]
    )
    var sampler: File? = null

    @CommandLine.Parameters(
            description = ["Arguments to pass to jmeter"]
    )
    val additionalJmeterParameters: MutableList<String> = mutableListOf()

    override fun call() {
        val temporaryDirectory = createTemporaryDirectory()
        val unpacker = ResourceUnpacker(javaClass.classLoader, "jmeter-home", temporaryDirectory)
        val unpackedFiles = unpacker.unpack()
        require(unpackedFiles.isNotEmpty()) { "Failed to unpack jmeter home" }

        val propertiesFile = unpackedFiles.first { it.name == "jmeter.properties" }

        log.info("Unpacked $unpackedFiles")

        val jmeterArguments = mutableListOf<String>().apply {
            // headless
            add("-n")

            // jmeter.properties
            add("-p")
            add(propertiesFile.absolutePath)

            // jmeter home directory
            add("--homedir")
            add(temporaryDirectory.absolutePath)

            // additional jmeter parameters
            addAll(additionalJmeterParameters)
        }
        log.info("Invoking jmeter with $jmeterArguments")

        // jmeter home directory
        System.setProperty("jmeter.home", temporaryDirectory.absolutePath)

        // sampler
        val localSampler = this.sampler
        if (localSampler != null) {
            System.setProperty("search_paths", localSampler.absolutePath)
        }

        NewDriver.main(jmeterArguments.toTypedArray())
    }

    private fun createTemporaryDirectory(): File {
        val temporaryDirectory = File(System.getProperty("java.io.tmpdir"), "com.r3.conclave.load-test")
        temporaryDirectory.mkdir()
        Runtime.getRuntime().addShutdownHook(Thread {
            temporaryDirectory.deleteRecursively()
        })
        return temporaryDirectory
    }
}
