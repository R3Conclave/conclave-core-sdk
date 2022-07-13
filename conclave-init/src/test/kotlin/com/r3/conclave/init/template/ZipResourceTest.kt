package com.r3.conclave.init.template

import com.r3.conclave.init.common.walkTopDown
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import kotlin.io.path.Path
import kotlin.io.path.createTempDirectory
import kotlin.io.path.isRegularFile
import kotlin.io.path.relativeTo

class ZipResourceTest {
    @Test
    // Not sure about this test as it will break whenever the template is modified
    fun `extract template`() {
        val outputDir = createTempDirectory()

        ZipResource("/template.zip", outputDir).extractFiles()

        val expected = listOf(
            "README.md",
            "client/build.gradle",
            "client/src/main/java/com/r3/conclave/template/client/TemplateEnclaveClient.java",
            "client/src/main/kotlin/com/r3/conclave/template/client/TemplateEnclaveClient.kt",
            "enclave/build.gradle",
            "enclave/src/test/java/com/r3/conclave/template/enclave/TemplateEnclaveTest.java",
            "enclave/src/main/java/com/r3/conclave/template/enclave/TemplateEnclave.java",
            "enclave/src/test/kotlin/com/r3/conclave/template/enclave/TemplateEnclaveTest.kt",
            "enclave/src/main/kotlin/com/r3/conclave/template/enclave/TemplateEnclave.kt",
            "build.gradle",
            "settings.gradle",
            "host/build.gradle"
        ).map(::Path).toSet()

        val outputFiles =
            outputDir.walkTopDown().filter { it.isRegularFile() }.map { it.relativeTo(outputDir) }.toSet()

        assertEquals(expected, outputFiles)
    }
}
