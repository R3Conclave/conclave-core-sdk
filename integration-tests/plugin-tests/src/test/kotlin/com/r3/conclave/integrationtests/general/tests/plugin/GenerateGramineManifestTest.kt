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

class GenerateGramineManifestTest : AbstractModeTaskTest(), TestWithSigning {
    companion object {
        @JvmStatic
        @BeforeAll
        fun check() {
            gramineOnlyTest()
        }
    }

    override val taskNameFormat: String get() = "generateGramineManifest%s"
    override val outputName: String get() = "java.manifest"

    @Test
    fun productID() {
        assertTaskIsIncremental {
            assertThat(manifest().getLong("sgx.isvprodid")).isEqualTo(11)
            updateBuildFile("productID = 11", "productID = 111")
        }
        assertThat(manifest().getLong("sgx.isvprodid")).isEqualTo(111)
    }

    @Test
    fun revocationLevel() {
        assertTaskIsIncremental {
            assertThat(manifest().getLong("sgx.isvsvn")).isEqualTo(13)
            updateBuildFile("revocationLevel = 12", "revocationLevel = 121")
        }
        assertThat(manifest().getLong("sgx.isvsvn")).isEqualTo(122)
    }

    @Test
    fun maxThreads() {
        assertThat(buildFile).content().doesNotContain("maxThreads")
        assertTaskIsIncremental {
            assertThat(manifest().getLong("sgx.thread_num")).isEqualTo(20)  // maxThreads * 2
            updateBuildFile("conclave {\n", "conclave {\nmaxThreads = 12\n")
        }
        assertThat(manifest().getLong("sgx.thread_num")).isEqualTo(24)
    }

    @ParameterizedTest
    @ValueSource(strings = ["dummyKey", "privateKey"])
    fun `signing key in simulation mode`(signingType: String) {
        simulationOnlyTest()

        // The signingKey config is specified in the enclaveMode block
        assertThat(buildFile).content().doesNotContain("${enclaveMode.name.lowercase()} {")
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
                ""
            }
            val enclaveModeBlock = """${enclaveMode.name.lowercase()} {
                |   signingType = $signingType
                |   $signingKeyConfig
                |}""".trimMargin()
            updateBuildFile("conclave {\n", "conclave {\n$enclaveModeBlock\n")

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
