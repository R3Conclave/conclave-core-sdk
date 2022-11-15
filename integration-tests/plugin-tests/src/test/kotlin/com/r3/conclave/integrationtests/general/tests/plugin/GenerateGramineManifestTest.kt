package com.r3.conclave.integrationtests.general.tests.plugin

import com.r3.conclave.integrationtests.general.commontest.TestUtils.gramineOnlyTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import kotlin.io.path.readLines

class GenerateGramineManifestTest : AbstractModeTaskTest() {
    companion object {
        @JvmStatic
        @BeforeAll
        fun check() {
            gramineOnlyTest()
        }
    }

    override val baseTaskName: String get() = "generateGramineManifest"
    override val outputName: String get() = "java.manifest"

    @Test
    fun maxThreads() {
        assertThat(buildFile).content().doesNotContain("maxThreads")
        assertTaskIsIncremental {
            assertThat(output.readLines()).contains("sgx.thread_num = 18")  // Default of 10 + 8
            updateBuildFile("conclave {\n", "conclave {\nmaxThreads = 12\n")
        }
        assertThat(output.readLines()).contains("sgx.thread_num = 20")
    }

//    @Test
//    fun `signing key`() {
//
//    }
}
