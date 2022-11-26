package com.r3.conclave.integrationtests.general.tests.plugin

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.extension.ExtensionContext
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.*
import org.junit.jupiter.params.provider.Arguments.arguments
import java.nio.file.Path
import java.util.*
import java.util.stream.Stream
import kotlin.io.path.div
import kotlin.io.path.reader

class GenerateEnclavePropertiesTest : AbstractTaskTest() {
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

    class OptionalConfigs : ArgumentsProvider {
        override fun provideArguments(context: ExtensionContext): Stream<Arguments> = Stream.of(
            arguments("enablePersistentMap", false, true)
        )
    }

    @ParameterizedTest
    @ArgumentsSource(OptionalConfigs::class)
    fun `simple optional config in enclave properties`(name: String, defaultValue: Any, newValue: Any) {
        assertThat(buildGradleFile).content().doesNotContain(name)
        assertTaskIsIncremental {
            assertThat(enclaveProperties()).containsEntry(name, defaultValue)
            addSimpleEnclaveConfig(name, newValue)
        }
        assertThat(enclaveProperties()).containsEntry(name, newValue)
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

    // TODO KDS

    private fun enclaveProperties(): Properties {
        return output.reader().use {
            Properties().apply { load(it) }
        }
    }
}
