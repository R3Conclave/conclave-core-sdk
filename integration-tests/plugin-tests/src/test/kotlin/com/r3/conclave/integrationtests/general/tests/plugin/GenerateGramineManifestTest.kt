package com.r3.conclave.integrationtests.general.tests.plugin

import com.r3.conclave.common.SHA256Hash
import com.r3.conclave.integrationtests.general.commontest.TestUtils
import com.r3.conclave.integrationtests.general.commontest.TestUtils.calculateMrsigner
import com.r3.conclave.integrationtests.general.commontest.TestUtils.enclaveMode
import com.r3.conclave.integrationtests.general.commontest.TestUtils.gramineOnlyTest
import com.r3.conclave.integrationtests.general.commontest.TestUtils.readSigningKey
import com.r3.conclave.integrationtests.general.commontest.TestUtils.simulationOnlyTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.tomlj.Toml
import org.tomlj.TomlTable
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.deleteExisting
import kotlin.io.path.div

class GenerateGramineManifestTest : AbstractPluginTaskTest() {
    companion object {
        @JvmStatic
        @BeforeAll
        fun precondition() {
            gramineOnlyTest()
        }
    }

    override val taskName: String get() = "generateGramineManifest${capitalisedEnclaveMode()}"
    override val output: Path get() = enclaveModeBuildDir / "java.manifest"

    @Test
    fun productID() {
        assertTaskIsIncremental {
            assertThat(manifest().getLong("sgx.isvprodid")).isEqualTo(11)
            modifyProductIdConfig(111)
        }
        assertThat(manifest().getLong("sgx.isvprodid")).isEqualTo(111)
    }

    @Test
    fun revocationLevel() {
        assertTaskIsIncremental {
            assertThat(manifest().getLong("sgx.isvsvn")).isEqualTo(13)
            modifyRevocationLevelConfig(121)
        }
        assertThat(manifest().getLong("sgx.isvsvn")).isEqualTo(122)
    }

    @Test
    fun maxThreads() {
        assertThat(buildGradleFile).content().doesNotContain("maxThreads")
        assertTaskIsIncremental {
            assertThat(manifest().getLong("sgx.thread_num")).isEqualTo(20)  // maxThreads * 2
            addSimpleEnclaveConfig("maxThreads", 12)
        }
        assertThat(manifest().getLong("sgx.thread_num")).isEqualTo(24)
    }

    @ParameterizedTest
    @ValueSource(strings = ["dummyKey", "privateKey"])
    fun `signing key in simulation mode`(signingType: String) {
        simulationOnlyTest()

        // The signingKey config is specified in the enclaveMode block
        assertThat(buildGradleFile).content().doesNotContain("${enclaveMode.name.lowercase()} {")
        assertThat(dummyKeyFile).doesNotExist()

        val signingKeyFile = assertTaskIsIncremental {
            // Make sure the dummy key is used when no signing key is specified.
            assertThat(dummyKeyFile).exists()
            assertManifestContainsSimulationMrsigner(dummyKeyFile)
            dummyKeyFile.deleteExisting()

            val signingKeyFile: Path
            val signingKeyConfig = if (signingType == "privateKey") {
                signingKeyFile = Files.createTempFile(buildDir, "signingKey", null).also(TestUtils::generateSigningKey)
                "signingKey = file('$signingKeyFile')"
            } else {
                signingKeyFile = dummyKeyFile
                ""  // dummyKey doesn't need any further config
            }
            addEnclaveModeConfig("""
                signingType = $signingType
                $signingKeyConfig
            """.trimIndent())

            signingKeyFile
        }

        assertManifestContainsSimulationMrsigner(signingKeyFile)
    }

    private fun assertManifestContainsSimulationMrsigner(keyFile: Path) {
        val mrsigner = calculateMrsigner(readSigningKey(keyFile))
        val mrsignerEnvVar = manifest().getString("loader.env.CONCLAVE_SIMULATION_MRSIGNER")
        checkNotNull(mrsignerEnvVar)
        assertThat(SHA256Hash.parse(mrsignerEnvVar)).isEqualTo(mrsigner)
    }

    private fun manifest(): TomlTable {
        val parseResult = Toml.parse(output)
        assertThat(parseResult.errors()).isEmpty()
        return parseResult
    }
}
