package com.r3.conclave.integrationtests.general.tests.plugin

import org.junit.jupiter.api.Disabled

@Disabled
class GenerateGramineManifestTest : AbstractModeTaskTest() {
    override val baseTaskName: String get() = "generateGramineManifest"
    override val outputName: String get() = "java.manifest"


}
