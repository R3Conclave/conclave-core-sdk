package com.r3.conclave.integrationtests.general.tests.plugin

import com.r3.conclave.integrationtests.general.commontest.TestUtils.enclaveMode
import java.nio.file.Path
import kotlin.io.path.div

abstract class AbstractModeTaskTest : AbstractConclaveTaskTest() {
    abstract val taskNameFormat: String

    override val taskName: String get() {
        val capitalisedMode = enclaveMode().name.lowercase().replaceFirstChar(Char::titlecase)
        return String.format(taskNameFormat, capitalisedMode)
    }
    override val output: Path get() = enclaveModeBuildDir / outputName

    val enclaveModeBuildDir: Path get() = conclaveBuildDir / enclaveMode().name.lowercase()
}
