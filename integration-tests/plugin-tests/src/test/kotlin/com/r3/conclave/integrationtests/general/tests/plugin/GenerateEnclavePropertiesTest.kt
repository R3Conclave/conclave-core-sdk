package com.r3.conclave.integrationtests.general.tests.plugin

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.*
import java.nio.file.Path
import java.util.*
import kotlin.io.path.div
import kotlin.io.path.reader

class GenerateEnclavePropertiesTest : AbstractPluginTaskTest() {
    override val taskName: String get() = "generateEnclaveProperties"
    override val output: Path get() = conclaveBuildDir / "enclave.properties"

    @ParameterizedTest
    @ValueSource(strings = ["productID", "revocationLevel"])
    fun `required config in enclave properties`(name: String) {
        assertTaskIsIncremental {
            if (name == "productID") {
                modifyProductIdConfig(100)
            } else {
                modifyRevocationLevelConfig(100)
            }
        }
        assertThat(enclaveProperties()).containsEntry(name, "100")
    }

    @ParameterizedTest
    @CsvSource(
        "enablePersistentMap, false, true"
    )
    fun `optional boolean config in enclave properties`(name: String, defaultValue: Boolean, newValue: Boolean) {
        assertThat(buildGradleFile).content().doesNotContain(name)
        assertTaskIsIncremental {
            assertThat(enclaveProperties()).containsEntry(name, defaultValue.toString())
            addSimpleEnclaveConfig(name, newValue)
        }
        assertThat(enclaveProperties()).containsEntry(name, newValue.toString())
    }

    @ParameterizedTest
    @CsvSource(
        "maxPersistentMapSize, 16777216, 32m, 33554432",
        "inMemoryFileSystemSize, 67108864, 100m, 104857600",
        "persistentFileSystemSize, 0, 1g, 1073741824"
    )
    fun `optional size bytes config in enclave properties`(name: String, defaultRawValue: Long, newBytesValue: String,
                                                           newRawValue: Long) {
        assertThat(buildGradleFile).content().doesNotContain(name)
        assertTaskIsIncremental {
            assertThat(enclaveProperties()).containsEntry(name, defaultRawValue.toString())
            addSimpleEnclaveConfig(name, newBytesValue)
        }
        assertThat(enclaveProperties()).containsEntry(name, newRawValue.toString())
    }

    @Test
    fun kdsEnclaveConstraint() {
        val kdsEnclaveConstraint = "S:B4CDF6F4FA5B484FCA82292CE340FF305AA294F19382178BEA759E30E7DCFE2D PROD:1 SEC:INSECURE"
        assertThat(buildGradleFile).content().doesNotContain("kdsEnclaveConstraint")
        assertTaskIsIncremental {
            assertThat(enclaveProperties()).doesNotContainKey("kds.kdsEnclaveConstraint")
            addEnclaveConfigBlock("""
                kds {
                    kdsEnclaveConstraint = "$kdsEnclaveConstraint"
                }
            """.trimIndent())
        }
        assertThat(enclaveProperties()).containsEntry("kds.kdsEnclaveConstraint", kdsEnclaveConstraint)
    }

//    @Test
//    fun persistenceKeySpec() {
//
//    }

    // TODO KDS

    private fun enclaveProperties(): Properties {
        return output.reader().use {
            Properties().apply { load(it) }
        }
    }
}
