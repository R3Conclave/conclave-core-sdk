package com.r3.conclave.plugin.enclave.gradle

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

internal class ResourcesConfigTest {
    private val mapper = jacksonObjectMapper()

    @Test
    fun generateResourcesConfig() {
        val outputFile = createTempFile()
        ResourcesConfig.create(includes = listOf(".*/intel-.*pem")).writeToFile(outputFile)
        val generatedJSON = mapper.readTree(outputFile)

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
