package com.r3.conclave.integrationtests.general.tests.plugin

import com.r3.conclave.integrationtests.general.commontest.TestUtils.RuntimeType.GRAMINE
import com.r3.conclave.integrationtests.general.commontest.TestUtils.assertEntryExists
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.nio.file.Path
import java.util.*
import java.util.jar.JarFile
import kotlin.io.path.div

class PythonEnclaveAdapterJarTest : AbstractPluginTaskTest() {
    override val taskName: String get() = "pythonEnclaveAdapterJar"
    override val output: Path get() = buildDir / "libs" / "$projectName-fat.jar"
    override val taskIsSpecificToRuntime get() = GRAMINE

    @BeforeEach
    fun convertProjectToPython() {
        changeToPythonEnclave()
    }

    @Test
    fun `contents of jar`() {
        runTask()
        JarFile(output.toFile()).use { jar ->
            jar.assertEntryExists("com/r3/conclave/python/PythonEnclaveAdapter.class")
            jar.assertEntryExists("com/r3/conclave/python/enclave.properties") {
                val enclaveProperties = Properties().apply { load(it) }
                assertThat(enclaveProperties).containsEntry("productID", "11")
                assertThat(enclaveProperties).containsEntry("revocationLevel", "12")
            }
        }
    }
}
