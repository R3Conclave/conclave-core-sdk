package com.r3.conclave.plugin.enclave.gradle

import com.r3.conclave.common.EnclaveConstraint
import com.r3.conclave.common.kds.MasterKeyType
import com.r3.conclave.plugin.enclave.gradle.extension.ConclaveExtension
import com.r3.conclave.plugin.enclave.gradle.extension.KeySpecExtension
import org.gradle.api.GradleException
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.OutputFile
import java.util.*
import javax.inject.Inject

open class GenerateEnclaveProperties @Inject constructor(
    objects: ObjectFactory,
    private val conclaveExtension: ConclaveExtension
) : ConclaveTask() {
    @get:Input
    val productID: Property<Int> = objects.intProperty()
    @get:Input
    val revocationLevel: Property<Int> = objects.intProperty()
    @get:Input
    val enablePersistentMap: Property<Boolean> = objects.booleanProperty()
    @get:Input
    val maxPersistentMapSize: Property<String> = objects.stringProperty()
    @get:Input
    val inMemoryFileSystemSize: Property<String> = objects.stringProperty()
    @get:Input
    val persistentFileSystemSize: Property<String> = objects.stringProperty()

    @get:OutputFile
    val enclavePropertiesFile: RegularFileProperty = objects.fileProperty()

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
        val kdsEnabled = conclaveExtension.kds.isPresent
        properties["kds.configurationPresent"] = kdsEnabled.toString()
        if (!kdsEnabled) {
            return
        }

        if (conclaveExtension.kds.kdsEnclaveConstraint.isPresent) {
            val kdsEnclaveConstraintString = conclaveExtension.kds.kdsEnclaveConstraint.get().toString()
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

        if (conclaveExtension.kds.keySpec.isPresent) {
            logger.warn("kds.keySpec has been replaced by kds.persistenceKeySpec and it is now deprecated")
            applyKDSConfigPersistenceKeySpec(properties, conclaveExtension.kds.keySpec)
            properties[persistenceKeySpecPropertyName] = "true"
        } else if (conclaveExtension.kds.persistenceKeySpec.isPresent) {
            applyKDSConfigPersistenceKeySpec(properties, conclaveExtension.kds.persistenceKeySpec)
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

    override fun action() {
        println("Inputs:")
        inputs.properties.forEach { println(it) }
        println()

        val properties = TreeMap<String, String>()

        properties["productID"] = productID.get().toString()
        properties["revocationLevel"] = revocationLevel.get().toString()
        properties["enablePersistentMap"] = enablePersistentMap.get().toString()
        properties["maxPersistentMapSize"] = maxPersistentMapSize.get().toSizeBytes().toString()
        properties["inMemoryFileSystemSize"] = inMemoryFileSystemSize.get().toSizeBytes().toString()
        properties["persistentFileSystemSize"] = persistentFileSystemSize.get().toSizeBytes().toString()

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
