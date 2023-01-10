package com.r3.conclave.integrationtests.general.tests.plugin

import com.r3.conclave.integrationtests.general.commontest.TestUtils.RuntimeType.GRAALVM
import java.nio.file.Path
import kotlin.io.path.div

/**
 * This runs the tests from [AbstractPluginTaskTest], in particular making sure it's incremental (i.e. doesn't run
 * again once GraalVM has been downloaded) which would otherwise lead to a poor developer experience.
 */
class CopyGraalVMTest : AbstractPluginTaskTest() {
    override val taskName: String get() = "copyGraalVM"
    override val output: Path get() = conclaveBuildDir / "graalvm"
    override val taskIsSpecificToRuntime get() = GRAALVM
}
