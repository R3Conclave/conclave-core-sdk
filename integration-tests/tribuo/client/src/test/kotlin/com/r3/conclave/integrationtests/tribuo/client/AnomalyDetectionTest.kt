package com.r3.conclave.integrationtests.tribuo.client

import com.r3.conclave.integrationtests.general.commontest.TestUtils
import com.r3.conclave.integrationtests.general.commontest.TestUtils.simulationOnlyTest
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.data.Offset
import org.junit.jupiter.api.*

@TestMethodOrder(MethodOrderer.OrderAnnotation::class)
class AnomalyDetectionTest : TribuoTest() {
    companion object {
        private val OFFSET = Offset.offset(0.000001)

        private lateinit var anomalyDetection: AnomalyDetection

        @BeforeAll
        @JvmStatic
        fun anomalyDetectionSetup() {
            anomalyDetection = AnomalyDetection(client)
        }
    }

    @Order(0)
    @Test
    fun trainAndEvaluate() {
        val evaluation = anomalyDetection.trainAndEvaluate()
        assertThat(evaluation.truePositives).isEqualTo(421L)
        assertThat(evaluation.falsePositives).isEqualTo(250L)
        assertThat(evaluation.falseNegatives).isEqualTo(0L)
        assertThat(evaluation.precision).isEqualTo(0.627422, OFFSET)
        assertThat(evaluation.recall).isEqualTo(1.0)
        assertThat(evaluation.f1).isEqualTo(0.771062, OFFSET)
    }

    @Order(1)
    @Test
    fun confusionMatrix() {
        // CON-1280: Multiple Tribuo tests fail for Gramine in debug mode
        if (TestUtils.runtimeType == TestUtils.RuntimeType.GRAMINE) {
            simulationOnlyTest()
        }
        val confusionMatrix = anomalyDetection.confusionMatrix().trim()
        assertThat(confusionMatrix).isEqualTo("""
            EXPECTED  ANOMALOUS
            EXPECTED         1,329        250
            ANOMALOUS            0        421
        """.trimIndent())
    }
}
