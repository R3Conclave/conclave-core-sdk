package com.r3.conclave.integrationtests.general.tests.plugin

import com.r3.conclave.integrationtests.general.commontest.TestUtils.gramineOnlyTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.nio.file.Path
import java.util.*
import java.util.jar.JarFile
import kotlin.io.path.createDirectories
import kotlin.io.path.div
import kotlin.io.path.writeText

class PythonEnclaveAdapterJarTest : AbstractTaskTest() {
    companion object {
        @JvmStatic
        @BeforeAll
        fun check() {
            gramineOnlyTest()
        }
    }

    override val taskName: String get() = "pythonEnclaveAdapterJar"
    override val output: Path get() = buildDir / "libs" / "$projectName-fat.jar"

    @BeforeEach
    fun convertProjectToPython() {
        val srcMain = projectDir / "src" / "main"
        srcMain.toFile().deleteRecursively()
        val pythonScript = srcMain / "python" / "enclave.py"
        pythonScript.parent.createDirectories()
        pythonScript.writeText("""
            def on_enclave_startup:
                print("Python enclave started")
        """.trimIndent())
    }

    @Test
    fun `contents of jar`() {
        runTask()
        JarFile(output.toFile()).use { jar ->
            assertThat(jar.getJarEntry("com/r3/conclave/python/PythonEnclaveAdapter.class")).isNotNull
            val enclavePropertiesEntry = jar.getJarEntry("com/r3/conclave/python/enclave.properties")
            assertThat(enclavePropertiesEntry).isNotNull
            val enclaveProperties = jar.getInputStream(enclavePropertiesEntry).use {
                Properties().apply { load(it) }
            }
            assertThat(enclaveProperties).containsEntry("productID", "11")
            assertThat(enclaveProperties).containsEntry("revocationLevel", "12")
        }
    }
}
