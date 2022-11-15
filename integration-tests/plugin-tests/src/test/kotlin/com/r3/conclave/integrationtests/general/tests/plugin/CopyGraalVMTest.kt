package com.r3.conclave.integrationtests.general.tests.plugin

import com.r3.conclave.integrationtests.general.commontest.TestUtils.graalvmOnlyTest
import org.junit.jupiter.api.BeforeAll

// This runs the tests from AbstractTaskTest, in particular making sure it's incremental (i.e. doesn't run once
// GraalVM has been downloaded) which would otherwise lead to a poor developer experience.
class CopyGraalVMTest : AbstractConclaveTaskTest() {
    companion object {
        @JvmStatic
        @BeforeAll
        fun check() {
            graalvmOnlyTest()
        }
    }

    override val taskName: String get() = "copyGraalVM"
    override val outputName: String get() = "graalvm"
}
