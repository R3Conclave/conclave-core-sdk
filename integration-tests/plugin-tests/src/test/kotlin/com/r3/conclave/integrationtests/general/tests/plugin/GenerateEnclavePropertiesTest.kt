package com.r3.conclave.integrationtests.general.tests.plugin

import com.r3.conclave.common.kds.MasterKeyType
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.*
import org.junitpioneer.jupiter.cartesian.CartesianTest
import org.junitpioneer.jupiter.cartesian.CartesianTest.Enum
import org.junitpioneer.jupiter.cartesian.CartesianTest.Values
import java.nio.file.Path
import java.util.*
import kotlin.io.path.div
import kotlin.io.path.reader

class GenerateEnclavePropertiesTest : AbstractPluginTaskTest() {
    companion object {
        private const val kdsEnclaveConstraint = "S:B4CDF6F4FA5B484FCA82292CE340FF305AA294F19382178BEA759E30E7DCFE2D " +
                "PROD:1 " +
                "SEC:INSECURE"
    }

    override val taskName: String get() = "generateEnclaveProperties"
    override val output: Path get() = conclaveBuildDir / "enclave.properties"

    @ParameterizedTest
    @ValueSource(strings = ["productID", "revocationLevel"])
    fun `required config in enclave properties`(name: String) {
        runTaskAfterInputChangeAndAssertItsIncremental {
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
        runTaskAfterInputChangeAndAssertItsIncremental {
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
        runTaskAfterInputChangeAndAssertItsIncremental {
            assertThat(enclaveProperties()).containsEntry(name, defaultRawValue.toString())
            addSimpleEnclaveConfig(name, newBytesValue)
        }
        assertThat(enclaveProperties()).containsEntry(name, newRawValue.toString())
    }

    @Test
    fun `kds-kdsEnclaveConstraint`() {
        assertThat(buildGradleFile).content().doesNotContain("kdsEnclaveConstraint")
        runTaskAfterInputChangeAndAssertItsIncremental {
            assertThat(enclaveProperties()).doesNotContainKey("kds.kdsEnclaveConstraint")
            addEnclaveConfigBlock("""
                kds {
                    kdsEnclaveConstraint = "$kdsEnclaveConstraint"
                }
            """.trimIndent())
        }
        assertThat(enclaveProperties()).containsEntry("kds.kdsEnclaveConstraint", kdsEnclaveConstraint)
    }

    @ParameterizedTest
    @EnumSource
    fun `kds-persistenceKeySpec-masterKeyType and constraint`(masterKeyType: MasterKeyType) {
        assertThat(buildGradleFile).content().doesNotContain("persistenceKeySpec")
        runTaskAfterInputChangeAndAssertItsIncremental {
            assertThat(enclaveProperties()).doesNotContainKey("kds.kdsEnclaveConstraint.persistenceKeySpec.masterKeyType")
            addEnclaveConfigBlock("""
                kds {
                    kdsEnclaveConstraint = "$kdsEnclaveConstraint"
                    persistenceKeySpec {
                        masterKeyType = "$masterKeyType"
                        policyConstraint {
                            constraint = "SEC:INSECURE"
                        }
                    }
                }
            """.trimIndent())
        }
        assertThat(enclaveProperties())
            .containsEntry("kds.persistenceKeySpec.masterKeyType", masterKeyType.toString())
            .containsEntry("kds.persistenceKeySpec.policyConstraint.constraint", "SEC:INSECURE")
    }

    @CartesianTest
    fun `kds-persistenceKeySpec-policyConstraint-useOwn config`(
        @Values(strings = ["useOwnCodeHash", "useOwnCodeSignerAndProductID"]) name: String,
        @Enum value: OptionalBooleanConfig
    ) {
        assertThat(buildGradleFile).content().doesNotContain("persistenceKeySpec")
        runTaskAfterInputChangeAndAssertItsIncremental {
            assertThat(enclaveProperties().keys).noneMatch { (it as String).startsWith("kds.persistenceKeySpec.") }
            addEnclaveConfigBlock("""
                kds {
                    kdsEnclaveConstraint = "$kdsEnclaveConstraint"
                    persistenceKeySpec {
                        masterKeyType = "DEVELOPMENT"
                        policyConstraint {
                            constraint = "SEC:INSECURE"
                            ${value.toConfigString(name)}
                        }
                    }
                }
            """.trimIndent())
        }
        assertThat(enclaveProperties()).containsEntry(
            "kds.persistenceKeySpec.policyConstraint.$name",
            value.toStringWithDefault(false)
        )
    }

    private fun enclaveProperties(): Properties {
        return output.reader().use {
            Properties().apply { load(it) }
        }
    }

    enum class OptionalBooleanConfig {
        ABSENT,
        TRUE,
        FALSE;

        fun toConfigString(name: String): String {
            return when (this) {
                ABSENT -> ""
                TRUE -> "$name = true"
                FALSE -> "$name = false"
            }
        }

        fun toStringWithDefault(defaultValue: Boolean): String {
            return when (this) {
                ABSENT -> defaultValue
                TRUE -> true
                FALSE -> false
            }.toString()
        }
    }
}
