package com.r3.conclave.integrationtests.general.tests.plugin

import com.r3.conclave.integrationtests.general.commontest.TestUtils.assertEntryExists
import com.r3.conclave.integrationtests.general.commontest.TestUtils.enclaveMode
import com.r3.conclave.integrationtests.general.commontest.TestUtils.graalvmOnlyTest
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import java.nio.file.Path
import java.util.jar.JarFile
import kotlin.io.path.div

/**
 * This test is similar to [GramineEnclaveBundleJarTest] in that it tests the enclaveBundleJar task but when the
 * runtime type is Graal VM.
 */
class GraalVMEnclaveBundleJarTest : AbstractTaskTest() {
    companion object {
        @JvmStatic
        @BeforeAll
        fun check() {
            graalvmOnlyTest()
        }
    }

    override val taskName: String get() {
        val capitalisedMode = enclaveMode.name.lowercase().replaceFirstChar(Char::titlecase)
        return "enclaveBundle${capitalisedMode}Jar"
    }

    override val output: Path get() {
        return buildDir / "libs" / "$projectName-bundle-${enclaveMode.name.lowercase()}.jar"
    }

    @Test
    fun `test run`() {
        assertTaskIsIncremental {
            updateBuildFile("productID = 11", "productID = 12")
        }


    }

    private val unsignedEnclave: Path get() = enclaveModeBuildDir / "enclave.so"

    private fun assertJarContents() {
        JarFile(output.toFile()).use { jar ->
            val bundlePath = "com/r3/conclave/enclave/user-bundles/com.test.enclave.TestEnclave/" +
                    "${enclaveMode.name.lowercase()}-graalvm.so"
            jar.assertEntryExists(bundlePath)
        }
    }
}
