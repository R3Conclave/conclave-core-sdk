package com.r3.sgx.enclavelethost.server

import com.r3.sgx.core.host.loggerFor
import picocli.CommandLine
import java.io.File
import java.lang.reflect.Field
import java.util.*
import java.util.concurrent.Callable

@CommandLine.Command(
        name = "enclavelet-host",
        description = ["Starts the enclavelet host server"],
        mixinStandardHelpOptions = true
)
class EnclaveletHostCli: Callable<Unit> {
    companion object {
        private val log = loggerFor<EnclaveletHostCli>()

        @JvmStatic
        fun main(args: Array<String>) {
            val cli = EnclaveletHostCli()
            val commandLine = CommandLine(cli)
            commandLine.addMixin("configuration", EnclaveletHostConfiguration.defaults.copy())
            val result = commandLine.parseArgs(*args)
            cli.configurationOverrides = configurationCliOverrides(commandLine, EnclaveletHostConfiguration::class.java)
            CommandLine.RunLast().handleParseResult(result)
        }

        // We allow configuration options to be overridden using CLI options
        private fun configurationCliOverrides(commandLine: CommandLine, configurationClass: Class<*>): Map<String, Any?> {
            val namesToField = HashMap<List<String>, Field>()
            for (field in configurationClass.declaredFields) {
                val option = field.annotations.mapNotNull { it as? CommandLine.Option }.singleOrNull() ?: continue
                namesToField[option.names.toList()] = field
            }
            val fieldOverrides = HashMap<String, Any?>()
            for (option in commandLine.commandSpec.options()) {
                val names = option.names().toList()
                val field = namesToField[names] ?: continue
                val value = option.typedValues().singleOrNull() ?: continue
                fieldOverrides[field.name] = value
            }
            return fieldOverrides
        }
    }

    @CommandLine.Option(
            names = ["-c", "--config"],
            description = ["Configuration file"]
    )
    var configuration: File? = null

    @CommandLine.Parameters(
            index = "0",
            description = ["The signed enclavelet binary file"]
    )
    lateinit var enclavelet: File

    lateinit var configurationOverrides: Map<String, Any?>

    override fun call() {
        val configuration = configuration?.let {
            EnclaveletHostConfiguration.readWithDefaults(it)
        } ?: EnclaveletHostConfiguration.defaults
        val overriddenConfiguration = configuration.withOverrides(configurationOverrides)
        val host = EnclaveletHostBuilder(overriddenConfiguration, enclavelet).build()
        host.use {
            it.start()
            log.info("Enclavelet host started on port ${host.configuration.bindPort}")
            it.await()
        }
    }

}
