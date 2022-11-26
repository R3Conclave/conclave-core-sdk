package com.r3.conclave.integrationtests.general.tests.plugin

import com.r3.conclave.integrationtests.general.commontest.TestUtils.assertEntryContents
import com.r3.conclave.integrationtests.general.commontest.TestUtils.gramineOnlyTest
import com.r3.conclave.integrationtests.general.commontest.TestUtils.readZipEntryNames
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.tomlj.Toml
import java.util.zip.ZipFile

class GramineDirectBundleZipTest : AbstractModeTaskTest() {
    companion object {
        private val pythonCode = """
            def receive_enclave_mail(mail):
                print("Mail!)
        """.trimIndent()

        @JvmStatic
        @BeforeAll
        fun precondition() {
            gramineOnlyTest()
        }
    }

    override val taskNameFormat: String get() = "gramineDirect%sBundleZip"
    override val outputName: String get() = "gramine-direct-bundle.zip"

    @ParameterizedTest
    @ValueSource(booleans = [false, true])
    fun `contents of zip`(isPython: Boolean) {
        if (isPython) {
            changeToPythonEnclave(pythonCode)
        }

        runTask()

        ZipFile(output.toFile()).use { zip ->
            zip.assertEntryContents("java.manifest") {
                assertThat(Toml.parse(it).getLong("sgx.isvprodid")).isEqualTo(11)
            }
            zip.assertEntryContents("enclave.jar") { jar ->
                val entryNames = jar.readZipEntryNames()
                if (isPython) {
                    assertThat(entryNames).contains("com/r3/conclave/python/PythonEnclaveAdapter.class")
                } else {
                    assertThat(entryNames).contains("com/test/enclave/TestEnclave.class")
                }
            }
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
            updateGradleBuildFile("productID = 11", "productID = 111")
        }
        ZipFile(output.toFile()).use { zip ->
            zip.assertEntryContents("java.manifest") {
                assertThat(Toml.parse(it).getLong("sgx.isvprodid")).isEqualTo(111)
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
}
