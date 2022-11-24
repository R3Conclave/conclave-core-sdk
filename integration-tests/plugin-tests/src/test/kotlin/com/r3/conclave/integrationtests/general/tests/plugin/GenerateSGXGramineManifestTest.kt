package com.r3.conclave.integrationtests.general.tests.plugin

import com.r3.conclave.integrationtests.general.commontest.TestUtils.gramineOnlyTest
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Disabled

@Disabled
class GenerateSGXGramineManifestTest : AbstractModeTaskTest(), TestWithSigning {
    companion object {
        @JvmStatic
        @BeforeAll
        fun check() {
            gramineOnlyTest()
        }
    }

    override val taskNameFormat: String get() = "generateSGXGramineManifest%s"
    override val outputName: String get() = TODO()
}
