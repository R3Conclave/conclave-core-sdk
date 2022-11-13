package com.r3.conclave.integrationtests.general.tests.plugin

import java.nio.file.Path
import kotlin.io.path.div

abstract class AbstractConclaveTaskTest : AbstractTaskTest() {
    abstract val outputName: String

    override val output: Path get() = conclaveBuildDir / outputName

    val conclaveBuildDir: Path get() = buildDir / "conclave"
}
