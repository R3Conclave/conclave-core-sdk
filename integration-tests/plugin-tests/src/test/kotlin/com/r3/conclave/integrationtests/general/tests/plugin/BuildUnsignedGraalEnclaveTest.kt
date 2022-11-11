package com.r3.conclave.integrationtests.general.tests.plugin

import com.r3.conclave.integrationtests.general.commontest.TestUtils.graalvmOnlyTest
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import java.nio.file.Path

class BuildUnsignedGraalEnclaveTest : AbstractModeTaskTest() {
    companion object {
        @JvmStatic
        @BeforeAll
        fun check() {
            graalvmOnlyTest()
        }
    }

    override val baseTaskName: String get() = "buildUnsignedGraalEnclave"
    override val outputFileName: String get() = "enclave.so"
    /**
     * Native image doesn't produce stable binaries.
     */
    override val isReproducible: Boolean get() = false

    @Test
    fun `incremental builds`() {
        assertTaskIsIncremental {
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
