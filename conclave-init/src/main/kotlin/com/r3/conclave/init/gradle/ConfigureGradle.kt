package com.r3.conclave.init.gradle

import com.r3.conclave.init.common.equals
import com.r3.conclave.init.common.printBlock
import java.nio.file.Path
import kotlin.io.path.absolutePathString
import kotlin.io.path.appendLines
import kotlin.io.path.createDirectories
import kotlin.io.path.createFile

fun configureGradleProperties(expectedConclaveRepo: Path, expectedConclaveVersion: String?) {
    val gradlePropertiesFile = GradlePropertiesFile.detect()

    var createPropertiesFile = true
    var setConclaveRepo = true
    var setConclaveVersion = true

    if (expectedConclaveVersion == null) setConclaveVersion = false

    if (gradlePropertiesFile.exists()) {
        createPropertiesFile = false

        with(gradlePropertiesFile.parse()) {
            if (conclaveRepo.equals(expectedConclaveRepo)) setConclaveRepo = false
            if (conclaveVersion == expectedConclaveVersion) setConclaveVersion = false
        }
    }

    val changesRequired = (createPropertiesFile || setConclaveRepo || setConclaveVersion)
    if (!changesRequired) return

    val permissionGranted = askForPermission(gradlePropertiesFile.path)
    if (!permissionGranted) return

    if (createPropertiesFile) {
        gradlePropertiesFile.path.parent.createDirectories()
        gradlePropertiesFile.path.createFile()
        gradlePropertiesFile.path.appendLines(listOf("# Created by Conclave Init"))
    } else if (setConclaveRepo || setConclaveVersion) {
        gradlePropertiesFile.path.appendLines(listOf("", "# Added by Conclave Init"))
    }

    if (setConclaveRepo) {
        gradlePropertiesFile.path.appendLines(listOf("conclaveRepo=${expectedConclaveRepo.toStringWithForwardSlashes()}"))
    }

    if (setConclaveVersion) {
        gradlePropertiesFile.path.appendLines(listOf("conclaveVersion=$expectedConclaveVersion"))
    }

    """
    Configuration of ${gradlePropertiesFile.path.absolutePathString()} is complete.
    """.printBlock()
}


// gradle.properties always requires forward slashes in paths, even on Windows
fun Path.toStringWithForwardSlashes(): String = toString().replace("\\", "/")


fun askForPermission(gradlePropertiesFile: Path): Boolean {
    val permissionGranted = yesNoPrompt(
        """
        The template project expects the conclaveRepo and conclaveVersion
        properties to be set in the user-wide gradle.properties file, but these
        properties were not found or do not match the current repo and version.
        
        See https://docs.conclave.net/gradle-properties.html for more information.
        
        Allow Conclave init to modify the properties file ${gradlePropertiesFile.absolutePathString()}?""".trimIndent()
    )

    if (!permissionGranted) {
        """
        WARNING: It will not be possible to compile the template project without setting
        the necessary gradle properties.
        
        See https://docs.conclave.net/gradle-properties.html for more information.
        """.printBlock()
    }

    return permissionGranted
}

fun yesNoPrompt(message: String): Boolean {
    print("\n${message} [y/n]: ")
    return when (readLine()) {
        "y" -> true
        "n" -> false
        else -> yesNoPrompt("Invalid response. Please respond with 'y' for yes or 'n' for no.")
    }
}
