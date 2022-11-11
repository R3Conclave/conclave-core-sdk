package com.r3.conclave.integrationtests.general.tests.plugin

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import java.nio.file.Path
import java.util.*
import java.util.jar.JarFile
import kotlin.io.path.div

class ShadowJarTest : AbstractTaskTest() {
    override val taskName: String get() = "shadowJar"
    override val outputFile: Path get() = buildDir / "libs" / "$projectName-fat-all.jar"

    @Test
    fun `contents of jar`() {
        runTask()
        JarFile(outputFile.toFile()).use { jar ->
            assertThat(jar.entries().toList().map { it.name }).contains(
                "com/test/enclave/TestEnclave.class",
                "com/test/enclave/enclave.properties"
            )
        }
    }

    @ParameterizedTest
    @CsvSource(
        "productID, 11, 111",
        "revocationLevel, 12, 121"
    )
    fun `required config in enclave properties`(name: String, currentValue: String, newValue: String) {
        assertTaskIsIncremental {
            assertThat(enclaveProperties()).containsEntry(name, currentValue)
            updateBuildFile("$name = $currentValue", "$name = $newValue")
        }
        assertThat(enclaveProperties()).containsEntry(name, newValue)
    }

    @ParameterizedTest
    @CsvSource(
        "enablePersistentMap, false, true"
    )
    fun `simple optional config in enclave properties`(name: String, defaultValue: String, newValue: String) {
        assertThat(buildFile).content().doesNotContain(name)
        assertTaskIsIncremental {
            assertThat(enclaveProperties()).containsEntry(name, defaultValue)
            updateBuildFile("conclave {\n", "conclave {\n$name = $newValue\n")
        }
        assertThat(enclaveProperties()).containsEntry(name, newValue)
    }

    @ParameterizedTest
    @CsvSource(
        "maxPersistentMapSize, 16777216, 32m, 33554432",
        "inMemoryFileSystemSize, 67108864, 100m, 104857600",
        "persistentFileSystemSize, 0, 1g, 1073741824"
    )
    fun `optional size bytes config in enclave properties`(name: String, defaultRawValue: String, newBytesValue: String,
                                                           newRawValue: String) {
        assertThat(buildFile).content().doesNotContain(name)
        assertTaskIsIncremental {
            assertThat(enclaveProperties()).containsEntry(name, defaultRawValue)
            updateBuildFile("conclave {\n", "conclave {\n$name = \"$newBytesValue\"\n")
        }
        assertThat(enclaveProperties()).containsEntry(name, newRawValue)
    }

    // TODO KDS

    private fun enclaveProperties(): Properties {
        return JarFile(outputFile.toFile()).use { jar ->
            jar.getInputStream(jar.getJarEntry("com/test/enclave/enclave.properties")).use {
                Properties().apply { load(it) }
            }
        }
    }
}
