package com.r3.conclave.plugin.enclave.gradle.util

import com.fasterxml.jackson.dataformat.xml.XmlMapper
import com.r3.conclave.plugin.enclave.gradle.BuildType
import com.r3.conclave.plugin.enclave.gradle.GenerateEnclaveConfigTest
import java.nio.file.Files
import java.nio.file.Path

class ProjectUtils {
    companion object {
        @JvmStatic
        fun loadEnclaveConfigurationFromFile(projectDirectory: Path, buildType: BuildType): GenerateEnclaveConfigTest.EnclaveConfiguration {
            val enclaveConfigurationFile = projectDirectory.resolve("build/conclave/${buildType.name.toLowerCase()}/enclave.xml")
            return XmlMapper().readValue(enclaveConfigurationFile.toFile(), GenerateEnclaveConfigTest.EnclaveConfiguration::class.java)
        }

        @JvmStatic
        fun replaceAndRewriteBuildFile(projectDirectory: Path, oldValue: String, newValue: String) {
            val projectFile = projectDirectory.resolve("build.gradle").toFile()
            val newProjectFile = projectFile.readText().replace(oldValue, newValue)
            Files.write(projectFile.toPath(), newProjectFile.toByteArray())
        }
    }
}