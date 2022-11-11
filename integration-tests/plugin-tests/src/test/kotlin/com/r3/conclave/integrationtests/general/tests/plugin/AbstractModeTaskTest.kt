package com.r3.conclave.integrationtests.general.tests.plugin

import java.nio.file.Path
import kotlin.io.path.div

abstract class AbstractModeTaskTest : AbstractConclaveTaskTest() {
    abstract val baseTaskName: String

    override val taskName: String get() = "$baseTaskName$enclaveMode"

    override val outputFile: Path get() = conclaveBuildDir / enclaveMode.lowercase() / outputFileName
}
