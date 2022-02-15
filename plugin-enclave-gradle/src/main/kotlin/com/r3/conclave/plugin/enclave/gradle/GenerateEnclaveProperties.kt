package com.r3.conclave.plugin.enclave.gradle

import com.r3.conclave.common.EnclaveConstraint
import com.r3.conclave.common.kds.MasterKeyType
import org.gradle.api.GradleException
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Internal
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.Paths
import java.util.*
import javax.inject.Inject

open class GenerateEnclaveProperties @Inject constructor(objects: ObjectFactory) : ConclaveTask() {
    @Input
    val conclaveExtension: Property<ConclaveExtension> = objects.property(ConclaveExtension::class.java)
    @Input
    val resourceDirectory: Property<String> = objects.property(String::class.java)
    @Input
    val mainClassName: Property<String> = objects.property(String::class.java)

    @get:Internal
    val enclavePropertiesFile: RegularFileProperty = objects.fileProperty()

    private fun throwKDSPropertyMissingException(propertyName: String) {
        throw GradleException(
                "KDS integration was enabled for this project, but $propertyName is missing from your enclave build " +
                "configuration. Please set a value for the missing property. If you don't know what this error " +
                "message is referring to, please consult the Conclave documentation and/or change notes."
        )
    }

    private fun kdsEnabled(): Boolean {
        // If any of the kds parameters are specified, assume that the user wants to use the KDS
        // TODO Find a better way of detecting "kds {...}" in the users build.gradle
        return conclaveExtension.get().kds.isPresent
    }

    private fun applyKDSConfig(properties: SortedMap<String, String>) {
        // If any of the kds parameters are specified, assume that the user wants to use the KDS
        // TODO Find a better way of detecting "kds {...}" in the users build.gradle
        val kdsEnabled = kdsEnabled()
        properties["kds.configurationPresent"] = kdsEnabled.toString()
        if (!kdsEnabled) {
            return
        }

        val conclave = this.conclaveExtension.get()

        if (conclave.kds.kdsEnclaveConstraint.isPresent) {
            val kdsEnclaveConstraintString = conclave.kds.kdsEnclaveConstraint.get().toString()
            try {
                EnclaveConstraint.parse(kdsEnclaveConstraintString)
            } catch (e: Exception) {
                throw GradleException("Error parsing kds.kdsEnclaveConstraint: ${e.message}")
            }
            properties["kds.kdsEnclaveConstraint"] = kdsEnclaveConstraintString
        } else {
            throwKDSPropertyMissingException("kds.kdsEnclaveConstraint")
        }

        if (conclave.kds.keySpec.isPresent) {
            logger.warn("kds.keySpec has been replaced by kds.persistenceKeySpec and it is now deprecated")
            applyKDSConfigPersistenceKeySpec(properties, conclave.kds.keySpec)
        } else {
            applyKDSConfigPersistenceKeySpec(properties, conclave.kds.persistenceKeySpec)
        }
    }

    private fun applyKDSConfigPersistenceKeySpec(properties: SortedMap<String, String>, keySpec: KeySpecExtension) {
        if (keySpec.masterKeyType.isPresent) {
            val masterKeyTypeString = keySpec.masterKeyType.get()
            val masterKeyType = try {
                MasterKeyType.valueOf(masterKeyTypeString.toUpperCase())
            } catch (e: IllegalArgumentException) {
                throw GradleException(
                    "Invalid KDS master key type '$masterKeyTypeString'. Valid values are: " +
                            MasterKeyType.values().joinToString(", "))
            }
            properties["kds.persistenceKeySpec.masterKeyType"] = masterKeyType.toString()
        } else {
            throwKDSPropertyMissingException("kds.persistenceKeySpec.masterKeyType")
        }

        if (keySpec.policyConstraint.constraint.isPresent) {
            val constraintsString = keySpec.policyConstraint.constraint.get()
            try {
                EnclaveConstraint.parse(constraintsString, false)
            } catch (e: Exception) {
                throw GradleException("Error parsing kds.persistenceKeySpec.policyConstraint.constraint: ${e.message}", e)
            }
            properties["kds.persistenceKeySpec.policyConstraint.constraint"] = constraintsString
        } else {
            throwKDSPropertyMissingException("kds.persistenceKeySpec.policyConstraint.constraint")
        }

        val useOwnCodeHash = keySpec.policyConstraint.useOwnCodeHash.getOrElse(false)
        val useOwnCodeSignerAndProductID = keySpec.policyConstraint.useOwnCodeSignerAndProductID.getOrElse(false)
        properties["kds.persistenceKeySpec.policyConstraint.useOwnCodeHash"] = useOwnCodeHash.toString()
        properties["kds.persistenceKeySpec.policyConstraint.useOwnCodeSignerAndProductID"] = useOwnCodeSignerAndProductID.toString()
    }

    override fun action() {
        val propertiesResourceDir = Paths.get(mainClassName.get().replace('.', '/')).parent
        val propertiesFile = Paths.get(resourceDirectory.get(), propertiesResourceDir.toString(), "enclave.properties")
        val properties = TreeMap<String, String>()

        val conclave = conclaveExtension.get()
        properties["productID"] = conclave.productID.get().toString()
        properties["revocationLevel"] = conclave.revocationLevel.get().toString()
        properties["enablePersistentMap"] = conclave.enablePersistentMap.get().toString()
        properties["maxPersistentMapSize"] = GenerateEnclaveConfig.getSizeBytes(conclave.maxPersistentMapSize.get()).toString()
        properties["inMemoryFileSystemSize"] = GenerateEnclaveConfig.getSizeBytes(conclave.inMemoryFileSystemSize.get()).toString()
        properties["persistentFileSystemSize"] = GenerateEnclaveConfig.getSizeBytes(conclave.persistentFileSystemSize.get()).toString()

        applyKDSConfig(properties)

        Files.createDirectories(propertiesFile.parent)
        FileOutputStream(propertiesFile.toFile()).use {
            it.write("# Build time enclave properties\n".toByteArray())
            for ((key, value) in properties) {
                it.write("$key=$value\n".toByteArray())
            }
        }
        enclavePropertiesFile.set(propertiesFile.toFile())
    }
}
