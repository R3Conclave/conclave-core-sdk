package com.r3.conclave.integrationtests.general.tests.plugin

// This runs the tests from AbstractTaskTest, in particular making sure it's incremental (i.e. doesn't run once
// GraalVM has been downloaded) which would otherwise lead to a poor developer experience.
class CopyGraalVMTest : AbstractConclaveTaskTest() {
    override val taskName: String get() = "copyGraalVM"
    override val outputName: String get() = "graalvm"
}
