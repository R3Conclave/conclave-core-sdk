package com.r3.conclave.integrationtests.general.tests.plugin

import java.nio.file.Path
import kotlin.io.path.div

abstract class AbstractModeTaskTest : AbstractPluginTaskTest() {
    abstract val taskNameFormat: String
    abstract val outputName: String

    override val taskName: String get() = String.format(taskNameFormat, capitalisedEnclaveMode())

    override val output: Path get() = enclaveModeBuildDir / outputName
}
