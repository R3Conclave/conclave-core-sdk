package com.r3.conclave.integrationtests.general.tests.plugin

import org.junit.jupiter.api.Test
import java.nio.file.Path
import kotlin.io.path.readText
import kotlin.io.path.writeText

class BuildUnsignedGraalEnclaveTest : AbstractPluginTaskTest("buildUnsignedGraalEnclave", modeDependent = true) {
    private val enclaveDir: Path get() = Path.of("$projectDir/enclave")

    @Test
    fun `incremental builds`() {
        // First fresh run and then make sure the second run is up-to-date.
        assertTaskRunIsIncremental()

        replaceAndRewriteBuildFile(
            enclaveDir,
            """maxHeapSize = "268435456"""",
            """maxHeapSize = "268435457""""
        )
        // Then check that the build runs again with the new build.gradle changes and then is up-to-date again.
        assertTaskRunIsIncremental()

        // Finally, update the native image config files and make sure they trigger a new build before reaching
        // up-to-date status.
        for (fileName in listOf("reflectionconfig.json", "serializationconfig.json")) {
            val file = Path.of("$enclaveDir/$fileName")
            file.writeText(file.readText().replace("Class0", "AnotherClass0"))
            assertTaskRunIsIncremental()
        }
    }
}
