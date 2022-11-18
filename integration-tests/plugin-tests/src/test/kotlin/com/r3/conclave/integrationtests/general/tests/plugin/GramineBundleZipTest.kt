package com.r3.conclave.integrationtests.general.tests.plugin

import org.junit.jupiter.api.Disabled

@Disabled
class GramineBundleZipTest : AbstractModeTaskTest() {
    override val taskNameFormat: String get() = "gramine%sBundleZip"
    override val outputName: String get() = TODO("Not yet implemented")
}