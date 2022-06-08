package com.r3.conclave.integrationtests.general.tests.plugin

import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.deleteExisting
import kotlin.streams.toList

class CopySubstrateDependenciesTest : AbstractPluginTaskTest("copySubstrateDependencies", modeDependent = true) {
    @Test
    fun `deleting random output forces task to re-run`() {
        assertTaskRunIsIncremental()

        val dependencyFiles = Files.walk(Path.of("$projectDir/build/conclave/com/r3/conclave/substratevm")).use {
            it.filter(Files::isRegularFile).toList()
        }
        val fileToDelete = dependencyFiles.random()
        println("Deleting $fileToDelete")
        fileToDelete.deleteExisting()

        assertTaskRunIsIncremental()
    }
}
