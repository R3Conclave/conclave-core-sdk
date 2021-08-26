package com.r3.conclave.plugin.enclave.gradle

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

internal class ResourcesConfigTest {
    @Test
    fun generateResourcesConfig() {
        var outputFile = createTempFile()
        ResourcesConfig.create(includes = listOf(".*/intel-.*pem")).writeToFile(outputFile)

        val expected = """
        {
            "resources": {
                "excludes": [],
                "includes": [
                    { "pattern": ".*/intel-.*pem" }
                ]
            }
        }
        """.removeWhitespace()

        assertEquals(expected, outputFile.readText())
    }
}

fun String.removeWhitespace() = filter { !it.isWhitespace() }
