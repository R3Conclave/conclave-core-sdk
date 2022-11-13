package com.r3.conclave.integrationtests.general.tests.plugin

import com.r3.conclave.integrationtests.general.commontest.TestUtils.graalvmOnlyTest
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Order
import org.junit.jupiter.api.Test
import java.nio.file.Path
import kotlin.io.path.copyTo

// We want this test to run first so that it can create an enclave.so file for other tests to use. We do this to
// avoid those tests rebuilding the enclave and incurring the lengthly build time of GraalVM enclaves.
@Order(0)
class BuildUnsignedGraalEnclaveTest : AbstractModeTaskTest() {
    companion object {
        @JvmStatic
        @BeforeAll
        fun check() {
            graalvmOnlyTest()
        }
    }

    override val baseTaskName: String get() = "buildUnsignedGraalEnclave"
    override val outputName: String get() = "enclave.so"
    /**
     * Native image doesn't produce stable binaries.
     */
    override val isReproducible: Boolean get() = false

    @Test
    fun `incremental builds`() {
        assertTaskIsIncremental {
            // Copy the enclave.so so that other tests can use it as input without having to rebuild the enclave.
            // This isn't at all ideal but the alternative is slow tests (or worse, missing tests). We intentionally
            // copy at this point to make sure the enclave matches the build.gradle config.
            output.copyTo(unsignedGraalEnclaveFile)
            updateBuildFile("productID = 11", "productID = 12")
        }

        // Finally, update the native image config files and make sure they trigger a new build before reaching
        // up-to-date status.
        for (fileName in listOf("reflectionconfig.json", "serializationconfig.json")) {
            val file = Path.of("$projectDir/$fileName")
            file.searchAndReplace("Class0", "AnotherClass0")
            assertTaskRunIsIncremental()
        }
    }
}
