package com.r3.conclave.integrationtests.general.tests.plugin

import com.r3.conclave.integrationtests.general.commontest.TestUtils
import com.r3.conclave.integrationtests.general.commontest.TestUtils.graalvmOnlyTest
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Disabled
import java.nio.file.Path
import kotlin.io.path.div

@Disabled
class GraalVMEnclaveBundleJar : AbstractTaskTest() {
    companion object {
        @JvmStatic
        @BeforeAll
        fun check() {
            graalvmOnlyTest()
        }
    }

    override val taskName: String get() {
        val capitalisedMode = TestUtils.enclaveMode.name.lowercase().replaceFirstChar(Char::titlecase)
        return "enclaveBundle${capitalisedMode}Jar"
    }

    override val output: Path get() {
        return buildDir / "libs" / "$projectName-bundle-${TestUtils.enclaveMode.name.lowercase()}.jar"
    }
}
