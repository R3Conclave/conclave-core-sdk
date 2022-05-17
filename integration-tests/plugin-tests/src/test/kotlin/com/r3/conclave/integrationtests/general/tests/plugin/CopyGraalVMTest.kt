package com.r3.conclave.integrationtests.general.tests.plugin

import org.junit.jupiter.api.Test
import java.nio.file.Path
import kotlin.io.path.deleteExisting

class CopyGraalVMTest : AbstractPluginTaskTest("copyGraalVM", modeDependent = false) {
    private val graalVMTarPath get() = "$projectDir/build/conclave/com/r3/conclave/graalvm/graalvm.tar"

    @Test
    fun `deleting output forces task to re-run`() {
        assertTaskRunIsIncremental()
        Path.of(graalVMTarPath).deleteExisting()
        assertTaskRunIsIncremental()
    }
}
