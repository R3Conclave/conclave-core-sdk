package com.r3.sgx.enclavelethost.server

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.r3.sgx.core.common.SgxQuoteType
import com.r3.sgx.core.host.EnclaveLoadMode
import picocli.CommandLine
import java.io.BufferedReader
import java.io.File
import java.io.InputStream
import java.io.InputStreamReader
import java.nio.file.Files
import java.util.*

@CommandLine.Command
data class EnclaveletHostConfiguration(
        @field:CommandLine.Option(
                names = ["-p", "--port"],
                description = ["The gRPC port to bind"]
        )
        @JvmField
        var bindPort: Int,

        @field:CommandLine.Option(
                names = ["-m", "--enclave-load-mode"],
                description = [
                    "Controls what mode the enclave is loaded in. Possible values: \${COMPLETION-CANDIDATES}"
                ]
        )
        @JvmField
        var enclaveLoadMode: EnclaveLoadMode,

        val threadPoolSize: Int,
        val epidSpid: String,
        val iasSubscriptionKey: String,
        val epidQuoteType: SgxQuoteType,
        val attestationServiceUrl: String,
        val mockAttestationServiceInSimulation: Boolean
) {
    companion object {
        private val mapper: ObjectMapper = ObjectMapper(YAMLFactory()).registerModule(KotlinModule())

        internal val defaults: EnclaveletHostConfiguration = read(this::class.java.getResourceAsStream("/default-host-settings.yml")).run {
            // Overwrite the placeholder values for epidSpid and iasSubscriptionKey from ra.properties.
            // These values are not in default-host-settings.yml as they're also needed by conclave-host.
            val raProperties = this::class.java.getResourceAsStream("/ra.properties").use {
                Properties().apply { load(it) }
            }
            val epidSpid = raProperties.getProperty("epidSpid")
            val iasSubscriptionKey = raProperties.getProperty("iasSubscriptionKey")
            copy(epidSpid = epidSpid, iasSubscriptionKey = iasSubscriptionKey)
        }

        fun read(configuration: File): EnclaveletHostConfiguration {
            return Files.newBufferedReader(configuration.toPath()).use {
                mapper.readValue(it, EnclaveletHostConfiguration::class.java)
            }
        }

        fun read(stream: InputStream): EnclaveletHostConfiguration {
            return BufferedReader(InputStreamReader(stream)).use {
                mapper.readValue(it, EnclaveletHostConfiguration::class.java)
            }
        }

        fun readWithDefaults(file: File): EnclaveletHostConfiguration {
            return Files.newBufferedReader(file.toPath()).use {
                mapper.readerForUpdating(defaults).readValue(it)
            }
        }
    }

    fun withOverrides(overrides: Map<String, Any?>): EnclaveletHostConfiguration {
        return mapper.updateValue(this.copy(), overrides)
    }
}
