package com.r3.conclave.integrationtests.general.tests.plugin

import java.nio.file.Path
import kotlin.io.path.div

abstract class AbstractConclaveTaskTest : AbstractTaskTest() {
    abstract val outputFileName: String

    override val outputFile: Path get() = conclaveBuildDir / outputFileName

    val conclaveBuildDir: Path get() = buildDir / "conclave"
}
