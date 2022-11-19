package com.r3.conclave.integrationtests.general.tests.plugin

import com.r3.conclave.integrationtests.general.commontest.TestUtils
import java.nio.file.Path
import kotlin.io.path.div

abstract class AbstractModeTaskTest : AbstractTaskTest() {
    abstract val taskNameFormat: String
    abstract val outputName: String

    override val taskName: String get() {
        val capitalisedMode = TestUtils.enclaveMode.name.lowercase().replaceFirstChar(Char::titlecase)
        return String.format(taskNameFormat, capitalisedMode)
    }

    override val output: Path get() = enclaveModeBuildDir / outputName
}
