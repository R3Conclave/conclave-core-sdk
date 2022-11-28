package com.r3.conclave.plugin.enclave.gradle

import com.r3.conclave.common.EnclaveConstraint
import com.r3.conclave.common.kds.MasterKeyType
import com.r3.conclave.plugin.enclave.gradle.extension.ConclaveExtension
import com.r3.conclave.plugin.enclave.gradle.extension.KeySpecExtension
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Nested
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction
import java.util.*

abstract class GenerateEnclaveProperties : DefaultTask() {
    @get:Nested
    abstract val conclaveExtension: Property<ConclaveExtension>

    @get:OutputFile
    abstract val enclavePropertiesFile: RegularFileProperty

    private fun throwKDSPropertyMissingException(propertyName: String) {
        throw GradleException(
            "KDS integration was enabled for this project, but $propertyName is missing from your enclave build " +
                    "configuration. Please set a value for the missing property. If you don't know what this error " +
                    "message is referring to, please consult the Conclave documentation and/or change notes."
        )
    }

    private fun applyKDSConfig(properties: SortedMap<String, String>) {
        // If any of the kds parameters are specified, assume that the user wants to use the KDS
        // TODO Find a better way of detecting "kds {...}" in the users build.gradle
        val kdsExtension = conclaveExtension.get().kds
        val kdsEnabled = kdsExtension.isPresent
        properties["kds.configurationPresent"] = kdsEnabled.toString()
        if (!kdsEnabled) {
            return
        }

        if (kdsExtension.kdsEnclaveConstraint.isPresent) {
            val kdsEnclaveConstraintString = kdsExtension.kdsEnclaveConstraint.get().toString()
            try {
                EnclaveConstraint.parse(kdsEnclaveConstraintString)
            } catch (e: Exception) {
                throw GradleException("Error parsing kds.kdsEnclaveConstraint: ${e.message}")
            }
            properties["kds.kdsEnclaveConstraint"] = kdsEnclaveConstraintString
        } else {
            throwKDSPropertyMissingException("kds.kdsEnclaveConstraint")
        }

        val persistenceKeySpecPropertyName = "kds.persistenceKeySpec.configurationPresent"

        if (kdsExtension.persistenceKeySpec.isPresent) {
            applyKDSConfigPersistenceKeySpec(properties, kdsExtension.persistenceKeySpec)
            properties[persistenceKeySpecPropertyName] = "true"
        } else {
            properties[persistenceKeySpecPropertyName] = "false"
        }
    }

    private fun applyKDSConfigPersistenceKeySpec(
        properties: SortedMap<String, String>,
        persistenceKeySpec: KeySpecExtension
    ) {
        if (persistenceKeySpec.masterKeyType.isPresent) {
            val masterKeyTypeString = persistenceKeySpec.masterKeyType.get()
            val masterKeyType = try {
                MasterKeyType.valueOf(masterKeyTypeString.uppercase())
            } catch (e: IllegalArgumentException) {
                throw GradleException(
                    "Invalid KDS master key type '$masterKeyTypeString'. Valid values are: " +
                            MasterKeyType.values().joinToString(", ")
                )
            }
            properties["kds.persistenceKeySpec.masterKeyType"] = masterKeyType.toString()
        } else {
            throwKDSPropertyMissingException("kds.persistenceKeySpec.masterKeyType")
        }

        if (persistenceKeySpec.policyConstraint.constraint.isPresent) {
            val constraintsString = persistenceKeySpec.policyConstraint.constraint.get()
            try {
                EnclaveConstraint.parse(constraintsString, false)
            } catch (e: Exception) {
                throw GradleException(
                    "Error parsing kds.persistenceKeySpec.policyConstraint.constraint: ${e.message}",
                    e
                )
            }
            properties["kds.persistenceKeySpec.policyConstraint.constraint"] = constraintsString
        } else {
            throwKDSPropertyMissingException("kds.persistenceKeySpec.policyConstraint.constraint")
        }
        val useOwnCodeHash = persistenceKeySpec.policyConstraint.useOwnCodeHash.getOrElse(false)
        val useOwnCodeSignerAndProductID =
            persistenceKeySpec.policyConstraint.useOwnCodeSignerAndProductID.getOrElse(false)
        properties["kds.persistenceKeySpec.policyConstraint.useOwnCodeHash"] = useOwnCodeHash.toString()
        properties["kds.persistenceKeySpec.policyConstraint.useOwnCodeSignerAndProductID"] =
            useOwnCodeSignerAndProductID.toString()
    }

    @TaskAction
    fun run() {
        // TODO Use inputs.properties to enumerate all property values and automatically dump them into the
        //  properties file
        val properties = TreeMap<String, String>()

        val conclave = conclaveExtension.get()
        properties["productID"] = conclave.productID.get().toString()
        properties["revocationLevel"] = conclave.revocationLevel.get().toString()
        properties["enablePersistentMap"] = conclave.enablePersistentMap.get().toString()
        properties["maxPersistentMapSize"] =
            GenerateEnclaveConfig.getSizeBytes(conclave.maxPersistentMapSize.get()).toString()
        properties["inMemoryFileSystemSize"] =
            GenerateEnclaveConfig.getSizeBytes(conclave.inMemoryFileSystemSize.get()).toString()
        properties["persistentFileSystemSize"] =
            GenerateEnclaveConfig.getSizeBytes(conclave.persistentFileSystemSize.get()).toString()

        applyKDSConfig(properties)

        enclavePropertiesFile.get().asFile.bufferedWriter().use { writer ->
            writer.write("# Build time enclave properties")
            writer.newLine()
            for ((key, value) in properties) {
                writer.write("$key=$value")
                writer.newLine()
            }
        }
    }
}
