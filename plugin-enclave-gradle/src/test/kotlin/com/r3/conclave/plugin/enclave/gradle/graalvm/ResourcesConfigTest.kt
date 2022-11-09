package com.r3.conclave.plugin.enclave.gradle.graalvm

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.r3.conclave.plugin.enclave.gradle.div
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.io.path.readText

class ResourcesConfigTest {
    private val mapper = jacksonObjectMapper()

    @Test
    fun generateResourcesConfig(@TempDir dir: Path) {
        val outputFile = dir / "output"
        ResourcesConfig.create(includes = listOf(".*/intel-.*pem")).writeToFile(outputFile.toFile())
        val generatedJSON = mapper.readTree(outputFile.readText())

        val expectedJSON = """
        {
            "resources": {
                "excludes": [],
                "includes": [
                    { "pattern": ".*/intel-.*pem" }
                ]
            }
        }
        """.let(mapper::readTree)

        assertEquals(expectedJSON, generatedJSON)
    }
}
