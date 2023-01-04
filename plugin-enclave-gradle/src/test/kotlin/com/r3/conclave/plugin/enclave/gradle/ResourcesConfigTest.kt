package com.r3.conclave.plugin.enclave.gradle

import org.gradle.internal.impldep.com.google.gson.Gson
import org.gradle.internal.impldep.com.google.gson.JsonParser
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.io.path.readText

class ResourcesConfigTest {
    private val gson = Gson()

    @Test
    fun generateResourcesConfig(@TempDir dir: Path) {
        val outputFile = dir / "output"
        ResourcesConfig.create(includes = listOf(".*/intel-.*pem")).writeToFile(outputFile.toFile())
        val generatedJSON = JsonParser.parseString(outputFile.readText())

        val expectedJSON = """
        {
            "resources": {
                "excludes": [],
                "includes": [
                    { "pattern": ".*/intel-.*pem" }
                ]
            }
        }
        """.let(JsonParser::parseString)

        assertEquals(expectedJSON, generatedJSON)
    }
}
