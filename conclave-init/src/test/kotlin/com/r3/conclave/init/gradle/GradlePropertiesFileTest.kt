package com.r3.conclave.init.gradle

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import kotlin.io.path.*

internal class GradlePropertiesFileTest {
    @Test
    fun `can detect correct gradle properties file`() {
        val home = createTempDirectory()
        val defaultDotGradle = (home / ".gradle").createDirectories()
        val defaultGradlePropertiesPath = (defaultDotGradle / "gradle.properties").createFile()

        val gradleUserHome = createTempDirectory()
        val customGradlePropertiesPath = (gradleUserHome / "gradle.properties").createFile()

        val defaultFilePath = GradlePropertiesFile.detect(gradleUserHome = null, userHome = home).path
        assertEquals(defaultFilePath, defaultGradlePropertiesPath)

        val customFilePath = GradlePropertiesFile.detect(gradleUserHome = gradleUserHome, userHome = home).path
        assertEquals(customFilePath, customGradlePropertiesPath)
    }

    @Test
    fun `can detect whether gradle properties file exists`() {
        val home = createTempDirectory()
        val dotGradle = home.resolve(".gradle").createDirectories()

        val file = GradlePropertiesFile.detect(gradleUserHome = null, userHome = home)
        assertFalse(file.exists())

        dotGradle.resolve("gradle.properties").createFile()
        assertTrue(file.exists())
    }

    @ParameterizedTest
    @MethodSource("conclaveProperties")
    fun `can parse gradle properties file`(propertiesFileContentsToExpectedProperties: contentsToProperties) {
        val (fileContents, expectedProperties) = propertiesFileContentsToExpectedProperties

        val gradlePropertiesFilePath = createTempFile().apply { writeText(fileContents) }

        val actualProperties = GradlePropertiesFile(gradlePropertiesFilePath).parse()
        assertEquals(expectedProperties, actualProperties)
    }

    companion object {
        @JvmStatic
        fun conclaveProperties(): List<contentsToProperties> = listOf(bothSet, repoNull, versionNull, bothNull)

        private val bothSet: contentsToProperties = """
            ## conclaveVersion=101
            conclaveRepo=/home/projects/conclave-sdk-101
            
            conclaveVersion=99
            anotherProperty=anotherValue
            # conclaveRepo=/home/projects/conclave-sdk-99
        """.trimIndent() to ConclaveProperties(Path("/home/projects/conclave-sdk-101"), "99")

        private val repoNull: contentsToProperties = """
            ## conclaveVersion=101
            
            conclaveVersion=99
            anotherProperty=anotherValue
            # conclaveRepo=/home/projects/conclave-sdk-99
        """.trimIndent() to ConclaveProperties(null, "99")

        private val versionNull: contentsToProperties = """
            ## conclaveVersion=101
            conclaveRepo=/home/projects/conclave-sdk-101
            
            anotherProperty=anotherValue
            # conclaveRepo=/home/projects/conclave-sdk-99
        """.trimIndent() to ConclaveProperties(Path("/home/projects/conclave-sdk-101"), null)

        private val bothNull: contentsToProperties = """
            ## conclaveVersion=101
            
            anotherProperty=anotherValue
            # conclaveRepo=/home/projects/conclave-sdk-99
        """.trimIndent() to ConclaveProperties(null, null)

    }
}

typealias contentsToProperties = Pair<String, ConclaveProperties>