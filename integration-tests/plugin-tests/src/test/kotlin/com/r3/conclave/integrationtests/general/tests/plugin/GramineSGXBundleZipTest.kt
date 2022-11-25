package com.r3.conclave.integrationtests.general.tests.plugin

import com.r3.conclave.common.internal.Cursor
import com.r3.conclave.common.internal.SgxMetadataCssBody.IsvProdId
import com.r3.conclave.common.internal.SgxMetadataEnclaveCss
import com.r3.conclave.common.internal.SgxMetadataEnclaveCss.body
import com.r3.conclave.common.internal.SgxMetadataEnclaveCss.key
import com.r3.conclave.common.internal.SgxTypesKt
import com.r3.conclave.integrationtests.general.commontest.TestUtils
import com.r3.conclave.integrationtests.general.commontest.TestUtils.assertEntryContents
import com.r3.conclave.integrationtests.general.commontest.TestUtils.assertEntryExists
import com.r3.conclave.integrationtests.general.commontest.TestUtils.calculateMrsigner
import com.r3.conclave.integrationtests.general.commontest.TestUtils.debugOnlyTest
import com.r3.conclave.integrationtests.general.commontest.TestUtils.enclaveMode
import com.r3.conclave.integrationtests.general.commontest.TestUtils.gramineOnlyTest
import com.r3.conclave.integrationtests.general.commontest.TestUtils.readSigningKey
import com.r3.conclave.integrationtests.general.commontest.TestUtils.readZipEntryNames
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.tomlj.Toml
import java.nio.file.Files
import java.nio.file.Path
import java.util.zip.ZipFile
import kotlin.io.path.deleteExisting

class GramineSGXBundleZipTest : AbstractModeTaskTest(), TestWithSigning {
    companion object {
        private val pythonCode = """
            def receive_enclave_mail(mail):
                print("Mail!)
        """.trimIndent()

        @JvmStatic
        @BeforeAll
        fun precondition() {
            gramineOnlyTest()
            debugOnlyTest()
        }
    }

    override val taskNameFormat: String get() = "gramineSGX%sBundleZip"
    override val outputName: String get() = "gramine-sgx-bundle.zip"

    @ParameterizedTest
    @ValueSource(booleans = [false, true])
    fun `contents of zip`(isPython: Boolean) {
        if (isPython) {
            changeToPythonEnclave(pythonCode)
        }

        runTask()

        ZipFile(output.toFile()).use { zip ->
            zip.assertEntryContents("java.manifest.sgx") {
                val manifest = Toml.parse(it)
                assertThat(manifest.getLong("sgx.isvprodid")).isEqualTo(11)
            }
            zip.assertEntryContents("enclave.jar") { jar ->
                val entryNames = jar.readZipEntryNames()
                if (isPython) {
                    assertThat(entryNames).contains("com/r3/conclave/python/PythonEnclaveAdapter.class")
                } else {
                    assertThat(entryNames).contains("com/test/enclave/TestEnclave.class")
                }
            }
            zip.assertEntryContents("java.sig") {
                val sigstruct = Cursor.wrap(SgxMetadataEnclaveCss.INSTANCE, it.readBytes())
                assertThat(sigstruct[body][IsvProdId].read()).isEqualTo(11)
            }
            zip.assertEntryExists("java.token")
            if (isPython) {
                zip.assertEntryContents("enclave.py") {
                    assertThat(it.reader().readText()).isEqualTo(pythonCode)
                }
            }
        }
    }

    @Test
    fun `changing enclave config`() {
        assertTaskIsIncremental {
            updateGradleBuildFile("conclave {\n", "conclave {\nproductID = 111\n")
        }
        ZipFile(output.toFile()).use { zip ->
            zip.assertEntryContents("java.manifest.sgx") {
                val manifest = Toml.parse(it)
                assertThat(manifest.getLong("sgx.isvprodid")).isEqualTo(111)
            }
            zip.assertEntryContents("java.sig") {
                val sigstruct = Cursor.wrap(SgxMetadataEnclaveCss.INSTANCE, it.readBytes())
                assertThat(sigstruct[body][IsvProdId].read()).isEqualTo(111)
            }
        }
    }

    @Test
    fun `switching from java to python code`() {
        assertTaskIsIncremental {
            changeToPythonEnclave(pythonCode)
        }
        ZipFile(output.toFile()).use { zip ->
            zip.assertEntryContents("enclave.jar") { jar ->
                assertThat(jar.readZipEntryNames())
                    .contains("com/r3/conclave/python/PythonEnclaveAdapter.class")
                    .doesNotContain("com/test/enclave/TestEnclave.class")
            }
        }
    }

    @ParameterizedTest
    @ValueSource(strings = ["dummyKey", "privateKey"])
    fun `signing key`(signingType: String) {
        // The signingKey config is specified in the enclaveMode block
        assertThat(buildGradleFile).content().doesNotContain("${enclaveMode.name.lowercase()} {")
        assertThat(dummyKeyFile).doesNotExist()

        val signingKeyFile = assertTaskIsIncremental {
            // Make sure the dummy key is used when no signing key is specified.
            assertThat(dummyKeyFile).exists()
            assertMrsigner(dummyKeyFile)
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
            updateGradleBuildFile("conclave {\n", "conclave {\n$enclaveModeBlock\n")

            signingKeyFile
        }

        assertMrsigner(signingKeyFile)
    }

    private fun assertMrsigner(keyFile: Path) {
        val mrsigner = calculateMrsigner(readSigningKey(keyFile))
        ZipFile(output.toFile()).use { zip ->
            zip.assertEntryContents("java.manifest.sgx") {
                val manifest = Toml.parse(it)
                assertThat(manifest.getString("loader.env.CONCLAVE_SIMULATION_MRSIGNER")).isNull()
            }
            zip.assertEntryContents("java.sig") {
                val sigstruct = Cursor.wrap(SgxMetadataEnclaveCss.INSTANCE, it.readBytes())
                assertThat(SgxTypesKt.getMrsigner(sigstruct[key])).isEqualTo(mrsigner)
            }
        }
    }
}
