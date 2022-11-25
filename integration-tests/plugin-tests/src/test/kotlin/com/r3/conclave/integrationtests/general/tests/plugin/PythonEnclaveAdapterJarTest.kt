package com.r3.conclave.integrationtests.general.tests.plugin

import com.r3.conclave.integrationtests.general.commontest.TestUtils.assertEntryContents
import com.r3.conclave.integrationtests.general.commontest.TestUtils.assertEntryExists
import com.r3.conclave.integrationtests.general.commontest.TestUtils.gramineOnlyTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.nio.file.Path
import java.util.*
import java.util.jar.JarFile
import kotlin.io.path.div

class PythonEnclaveAdapterJarTest : AbstractTaskTest() {
    companion object {
        @JvmStatic
        @BeforeAll
        fun precondition() {
            gramineOnlyTest()
        }
    }

    override val taskName: String get() = "pythonEnclaveAdapterJar"
    override val output: Path get() = buildDir / "libs" / "$projectName-fat.jar"

    @BeforeEach
    fun convertProjectToPython() {
        changeToPythonEnclave()
    }

    @Test
    fun `contents of jar`() {
        runTask()
        JarFile(output.toFile()).use { jar ->
            jar.assertEntryExists("com/r3/conclave/python/PythonEnclaveAdapter.class")
            jar.assertEntryContents("com/r3/conclave/python/enclave.properties") {
                val enclaveProperties = Properties().apply { load(it) }
                assertThat(enclaveProperties).containsEntry("productID", "11")
                assertThat(enclaveProperties).containsEntry("revocationLevel", "12")
            }
        }
    }
}
