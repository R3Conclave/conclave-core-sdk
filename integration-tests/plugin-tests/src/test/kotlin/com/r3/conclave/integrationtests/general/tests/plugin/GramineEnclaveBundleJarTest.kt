package com.r3.conclave.integrationtests.general.tests.plugin

import com.r3.conclave.integrationtests.general.commontest.TestUtils.ITEnclaveMode
import com.r3.conclave.integrationtests.general.commontest.TestUtils.RuntimeType.GRAMINE
import com.r3.conclave.integrationtests.general.commontest.TestUtils.assertEntryExists
import com.r3.conclave.integrationtests.general.commontest.TestUtils.enclaveMode
import com.r3.conclave.integrationtests.general.commontest.TestUtils.readZipEntryNames
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.nio.file.Path
import java.util.jar.JarFile
import kotlin.io.path.div

class GramineEnclaveBundleJarTest : AbstractPluginTaskTest() {
    override val taskName: String get() = "enclaveBundle${capitalisedEnclaveMode()}Jar"
    override val output: Path get() {
        return buildDir / "libs" / "$projectName-bundle-${enclaveMode.name.lowercase()}.jar"
    }
    override val taskIsSpecificToRuntime get() = GRAMINE

    @Test
    fun `contents of jar`() {
        runTaskAfterInputChangeAndAssertItsIncremental {
            assertJarContents("com.test.enclave.TestEnclave")
            val enclaveCode = projectDir.resolve("src/main/kotlin/com/test/enclave/EnclaveTest.kt")
            enclaveCode.searchAndReplace("TestEnclave", "TestEnclave2")
        }
        assertJarContents("com.test.enclave.TestEnclave2")
    }

    private fun assertJarContents(enclaveClassName: String) {
        JarFile(output.toFile()).use { bundleJar ->
            val bundlePath = "com/r3/conclave/enclave/user-bundles/$enclaveClassName/" +
                    "${enclaveMode.name.lowercase()}-gramine.zip"
            bundleJar.assertEntryExists(bundlePath) { gramineZip ->
                val actualEntryNames = gramineZip.readZipEntryNames()
                val expectedEntryNames = mutableListOf("enclave.jar")
                when (enclaveMode) {
                    ITEnclaveMode.SIMULATION -> expectedEntryNames += "java.manifest"
                    ITEnclaveMode.DEBUG -> expectedEntryNames += listOf("java.manifest.sgx", "java.sig", "java.token")
                }
                assertThat(actualEntryNames).containsOnlyOnceElementsOf(expectedEntryNames)
            }
        }
    }
}
