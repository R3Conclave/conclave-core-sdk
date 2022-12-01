package com.r3.conclave.integrationtests.general.tests.plugin

import com.r3.conclave.common.SHA256Hash
import com.r3.conclave.common.internal.Cursor
import com.r3.conclave.common.internal.SgxMetadataCssBody.IsvProdId
import com.r3.conclave.common.internal.SgxMetadataCssBody.IsvSvn
import com.r3.conclave.common.internal.SgxMetadataEnclaveCss
import com.r3.conclave.common.internal.SgxMetadataEnclaveCss.body
import com.r3.conclave.common.internal.SgxMetadataEnclaveCss.key
import com.r3.conclave.common.internal.SgxTypesKt
import com.r3.conclave.integrationtests.general.common.ByteCursor
import com.r3.conclave.integrationtests.general.commontest.TestUtils
import com.r3.conclave.integrationtests.general.commontest.TestUtils.ITEnclaveMode.DEBUG
import com.r3.conclave.integrationtests.general.commontest.TestUtils.ITEnclaveMode.SIMULATION
import com.r3.conclave.integrationtests.general.commontest.TestUtils.RuntimeType.GRAMINE
import com.r3.conclave.integrationtests.general.commontest.TestUtils.assertEntryExists
import com.r3.conclave.integrationtests.general.commontest.TestUtils.calculateMrsigner
import com.r3.conclave.integrationtests.general.commontest.TestUtils.enclaveMode
import com.r3.conclave.integrationtests.general.commontest.TestUtils.readSigningKey
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.tomlj.Toml
import org.tomlj.TomlTable
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption.REPLACE_EXISTING
import java.util.zip.ZipFile
import kotlin.io.path.deleteExisting
import kotlin.io.path.div

class GramineBundleZipTest : AbstractPluginTaskTest() {
    companion object {
        private val pythonCode = """
            def receive_enclave_mail(mail):
                print("Mail!)
        """.trimIndent()
    }

    override val taskName: String get() = "gramine${capitalisedEnclaveMode()}BundleZip"
    override val output: Path get() = enclaveModeBuildDir / "gramine-bundle.zip"
    override val taskIsSpecificToRuntime get() = GRAMINE

    @field:TempDir
    lateinit var tempDir: Path

    @Test
    fun productID() {
        assertTaskIsIncrementalUponInputChange {
            with(bundle()) {
                assertThat(manifest.getLong("sgx.isvprodid")).isEqualTo(11)
                if (sigstruct != null) {
                    assertThat(sigstruct[body][IsvProdId].read()).isEqualTo(11)
                }
            }
            modifyProductIdConfig(111)
        }
        with(bundle()) {
            assertThat(manifest.getLong("sgx.isvprodid")).isEqualTo(111)
            if (sigstruct != null) {
                assertThat(sigstruct[body][IsvProdId].read()).isEqualTo(111)
            }
        }
    }

    @Test
    fun revocationLevel() {
        assertTaskIsIncrementalUponInputChange {
            with(bundle()) {
                assertThat(manifest.getLong("sgx.isvsvn")).isEqualTo(13)
                if (sigstruct != null) {
                    assertThat(sigstruct[body][IsvSvn].read()).isEqualTo(13)
                }
            }
            modifyRevocationLevelConfig(121)
        }
        with(bundle()) {
            assertThat(manifest.getLong("sgx.isvsvn")).isEqualTo(122)
            if (sigstruct != null) {
                assertThat(sigstruct[body][IsvSvn].read()).isEqualTo(122)
            }
        }
    }

    @Test
    fun maxThreads() {
        assertThat(buildGradleFile).content().doesNotContain("maxThreads")
        assertTaskIsIncrementalUponInputChange {
            assertThat(bundle().manifest.getLong("sgx.thread_num")).isEqualTo(100)
            addSimpleEnclaveConfig("maxThreads", 12)
        }
        assertThat(bundle().manifest.getLong("sgx.thread_num")).isEqualTo(12)
    }

    @ParameterizedTest
    @ValueSource(strings = ["dummyKey", "privateKey"])
    fun `signing key`(signingType: String) {
        // The signingKey config is specified in the enclaveMode block
        assertThat(buildGradleFile).content().doesNotContain("${enclaveMode.name.lowercase()} {")
        assertThat(dummyKeyFile).doesNotExist()

        val signingKeyFile = assertTaskIsIncrementalUponInputChange {
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
                ""  // dummyKey doesn't need any further config
            }
            addEnclaveModeConfig("""
                signingType = $signingType
                $signingKeyConfig
            """.trimIndent())

            signingKeyFile
        }

        assertMrsigner(signingKeyFile)
    }

    @Test
    fun `switching from java to python code`() {
        assertTaskIsIncrementalUponInputChange {
            with(bundle()) {
                assertThat(pythonScriptContent).isNull()
                ZipFile(enclaveJar.toFile()).use {
                    it.assertEntryExists("com/test/enclave/TestEnclave.class")
                    assertThat(it.getEntry("com/r3/conclave/python/PythonEnclaveAdapter.class")).isNull()
                }
            }
            changeToPythonEnclave(pythonCode)
        }
        with(bundle()) {
            assertThat(pythonScriptContent).isEqualTo(pythonCode)
            ZipFile(enclaveJar.toFile()).use {
                it.assertEntryExists("com/r3/conclave/python/PythonEnclaveAdapter.class")
                assertThat(it.getEntry("com/test/enclave/TestEnclave.class")).isNull()
            }
        }
    }

    private fun assertMrsigner(keyFile: Path) {
        val mrsigner = calculateMrsigner(readSigningKey(keyFile))
        with(bundle()) {
            if (sigstruct != null) {
                assertThat(SgxTypesKt.getMrsigner(sigstruct[key])).isEqualTo(mrsigner)
                assertThat(manifest.getString("loader.env.CONCLAVE_SIMULATION_MRSIGNER")).isNull()
            } else {
                val mrsignerEnvVar = manifest.getString("loader.env.CONCLAVE_SIMULATION_MRSIGNER")
                assertThat(mrsignerEnvVar).isNotNull
                assertThat(SHA256Hash.parse(mrsignerEnvVar!!)).isEqualTo(mrsigner)
            }
        }
    }

    private fun bundle(): Bundle {
        return ZipFile(output.toFile()).use { zip ->
            val manifestEntryName = if (enclaveMode == SIMULATION) "java.manifest" else "java.manifest.sgx"
            val manifest = zip.assertEntryExists(manifestEntryName) {
                val parseResult = Toml.parse(it)
                assertThat(parseResult.errors()).isEmpty()
                parseResult
            }
            val enclaveJar = Files.createTempFile(tempDir, "enclave", ".jar")
            zip.assertEntryExists("enclave.jar") {
                Files.copy(it, enclaveJar, REPLACE_EXISTING)
            }
            val pythonScript = zip.getEntry("enclave.py")?.let {
                zip.getInputStream(it).use { py -> py.reader().readText() }
            }
            val sigstruct = if (enclaveMode == DEBUG) {
                zip.assertEntryExists("java.token")
                zip.assertEntryExists("java.sig") {
                    Cursor.wrap(SgxMetadataEnclaveCss.INSTANCE, it.readBytes())
                }
            } else {
                assertThat(zip.getEntry("java.sig")).isNull()
                assertThat(zip.getEntry("java.token")).isNull()
                null
            }
            Bundle(manifest, enclaveJar, pythonScript, sigstruct)
        }
    }

    private class Bundle(
        val manifest: TomlTable,
        val enclaveJar: Path,
        val pythonScriptContent: String?,
        val sigstruct: ByteCursor<SgxMetadataEnclaveCss>?
    )
}
