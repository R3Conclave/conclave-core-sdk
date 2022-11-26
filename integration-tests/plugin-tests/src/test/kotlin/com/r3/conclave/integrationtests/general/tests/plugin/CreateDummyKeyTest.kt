package com.r3.conclave.integrationtests.general.tests.plugin

import com.r3.conclave.integrationtests.general.commontest.TestUtils.calculateMrsigner
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.nio.file.Path
import kotlin.io.path.deleteIfExists

class CreateDummyKeyTest : AbstractTaskTest() {
    override val taskName: String get() = "createDummyKey"
    override val output: Path get() = dummyKeyFile
    /**
     * The dummy key is meant to be random.
     */
    override val isReproducible: Boolean get() = false

    @Test
    fun `output is an RSA key in PEM format with public exponent of 3`() {
        runTask()
        assertThat(dummyKey().publicExponent).isEqualTo(3)
    }

    @Test
    fun `key is random`() {
        val mrsigners = Array(10) {
            output.deleteIfExists()
            runTask()
            calculateMrsigner(dummyKey())
        }
        assertThat(mrsigners).doesNotHaveDuplicates()
    }
}
