package com.r3.conclave.integrationtests.general.tests.plugin

import org.junit.jupiter.api.Disabled

@Disabled
class CopyGraalVMTest : AbstractConclaveTaskTest() {
    override val taskName: String get() = "copyGraalVM"
    override val outputFileName: String
        get() = TODO("Not yet implemented")

}
