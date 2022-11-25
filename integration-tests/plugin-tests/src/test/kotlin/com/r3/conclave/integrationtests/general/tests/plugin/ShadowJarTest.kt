package com.r3.conclave.integrationtests.general.tests.plugin

import com.r3.conclave.integrationtests.general.commontest.TestUtils.assertEntryContents
import com.r3.conclave.integrationtests.general.commontest.TestUtils.assertEntryExists
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.nio.file.Path
import java.util.*
import java.util.jar.JarFile
import kotlin.io.path.div

class ShadowJarTest : AbstractTaskTest() {
    override val taskName: String get() = "shadowJar"
    override val output: Path get() = buildDir / "libs" / "$projectName-fat-all.jar"

    @Test
    fun `contents of jar`() {
        runTask()
        JarFile(output.toFile()).use { jar ->
            jar.assertEntryExists("com/test/enclave/TestEnclave.class")
            jar.assertEntryContents("com/test/enclave/enclave.properties") {
                val enclaveProperties = Properties().apply { load(it) }
                assertThat(enclaveProperties)
                    .containsEntry("productID", "11")
                    .containsEntry("revocationLevel", "12")
            }
        }
    }
}
