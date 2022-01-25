package com.r3.conclave.integrationtests.tribuo.client

import com.r3.conclave.integrationtests.tribuo.common.ClassEvaluationResult
import com.r3.conclave.integrationtests.tribuo.common.Configuration.Companion.MNIST_LOGISTIC_CONFIG_FILE_NAME
import com.r3.conclave.integrationtests.tribuo.common.Configuration.Companion.MNIST_TRANSFORMED_LOGISTIC_CONFIG_FILE_NAME
import com.r3.conclave.integrationtests.tribuo.common.Configuration.Companion.T10K_IMAGES_IDX_3_FILE_NAME
import com.r3.conclave.integrationtests.tribuo.common.Configuration.Companion.T10K_LABELS_IDX_1_FILE_NAME
import com.r3.conclave.integrationtests.tribuo.common.Configuration.Companion.TRAIN_IMAGES_IDX_3_FILE_NAME
import com.r3.conclave.integrationtests.tribuo.common.Configuration.Companion.TRAIN_LABELS_IDX_1_FILE_NAME
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.data.Offset
import org.junit.jupiter.api.*
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.ExtensionContext
import org.junit.jupiter.api.extension.TestWatcher
import java.nio.file.Paths
import kotlin.io.path.deleteIfExists

class ConfigurationWatcher : TestWatcher {
    override fun testFailed(context: ExtensionContext?, cause: Throwable?) {
        ConfigurationTest.teardownConfiguration()
    }
}

@TestMethodOrder(MethodOrderer.OrderAnnotation::class)
@ExtendWith(ConfigurationWatcher::class)
class ConfigurationTest : TribuoTest() {
    companion object {
        private lateinit var configuration: Configuration
        private val OFFSET = Offset.offset(0.001)

        @BeforeAll
        @JvmStatic
        fun setupConfiguration() {
            configuration = Configuration(client)
        }

        @AfterAll
        @JvmStatic
        fun teardownConfiguration() {
            if (Companion::configuration.isInitialized) {
                configuration.close()
            }
            if (System.getProperty("enclaveMode") == "mock") {
                Paths.get(client.resolve(MNIST_LOGISTIC_CONFIG_FILE_NAME)).deleteIfExists()
                Paths.get(client.resolve(MNIST_TRANSFORMED_LOGISTIC_CONFIG_FILE_NAME)).deleteIfExists()
            }
        }
    }

    @Order(0)
    @Test
    fun dataStats() {
        val dataStats = configuration.dataStats().trim()
        assertThat(dataStats).isEqualTo("""
            Training data size = 60000, number of features = 717, number of classes = 10
            Testing data size = 10000, number of features = 668, number of classes = 10
        """.trimIndent())
    }

    @Order(1)
    @Test
    fun initializeLogisticTrainer() {
        val initializeLogisticTrainer = configuration.initializeLogisticTrainer()
        assertThat(initializeLogisticTrainer).isEqualTo("""
            LinearSGDTrainer(objective=LogMulticlass,optimiser=AdaGrad(initialLearningRate=0.5,epsilon=0.01,initialValue=0.0),epochs=2,minibatchSize=1,seed=1)
        """.trimIndent())
    }

    @Order(2)
    @Test
    fun mnistLogisticConfig() {
        val mnistLogisticConfig = String(configuration.mnistLogisticConfig())
        assertThat(mnistLogisticConfig).isEqualTo("""
            {
              "config" : {
                "components" : [ {
                  "name" : "idxdatasource-1",
                  "type" : "org.tribuo.datasource.IDXDataSource",
                  "export" : "false",
                  "import" : "false",
                  "properties" : {
                    "outputPath" : "${client.resolve(TRAIN_LABELS_IDX_1_FILE_NAME)}",
                    "outputFactory" : "labelfactory-4",
                    "featuresPath" : "${client.resolve(TRAIN_IMAGES_IDX_3_FILE_NAME)}"
                  }
                }, {
                  "name" : "linearsgdtrainer-0",
                  "type" : "org.tribuo.classification.sgd.linear.LinearSGDTrainer",
                  "export" : "false",
                  "import" : "false",
                  "properties" : {
                    "seed" : "1",
                    "minibatchSize" : "1",
                    "shuffle" : "true",
                    "epochs" : "2",
                    "optimiser" : "adagrad-2",
                    "objective" : "logmulticlass-3",
                    "loggingInterval" : "10000"
                  }
                }, {
                  "name" : "adagrad-2",
                  "type" : "org.tribuo.math.optimisers.AdaGrad",
                  "export" : "false",
                  "import" : "false",
                  "properties" : {
                    "epsilon" : "0.01",
                    "initialLearningRate" : "0.5",
                    "initialValue" : "0.0"
                  }
                }, {
                  "name" : "labelfactory-4",
                  "type" : "org.tribuo.classification.LabelFactory",
                  "export" : "false",
                  "import" : "false"
                }, {
                  "name" : "logmulticlass-3",
                  "type" : "org.tribuo.classification.sgd.objectives.LogMulticlass",
                  "export" : "false",
                  "import" : "false"
                } ]
              }
            }
        """.trimIndent())
    }

    @Order(3)
    @Test
    fun lrEvaluator() {
        val lrEvaluator = configuration.lrEvaluator()
        val expectedResults = arrayOf(
                ClassEvaluationResult(980.0, 904.0, 76.0, 21.0, 0.922, 0.977, 0.949),
                ClassEvaluationResult(1135.0, 1072.0, 63.0, 18.0, 0.944, 0.983, 0.964),
                ClassEvaluationResult(1032.0, 856.0, 176.0, 56.0, 0.829, 0.939, 0.881),
                ClassEvaluationResult(1010.0, 844.0, 166.0, 84.0, 0.836, 0.909, 0.871),
                ClassEvaluationResult(982.0, 888.0, 94.0, 72.0, 0.904, 0.925, 0.915),
                ClassEvaluationResult(892.0, 751.0, 141.0, 143.0, 0.842, 0.840, 0.841),
                ClassEvaluationResult(958.0, 938.0, 20.0, 139.0, 0.979, 0.871, 0.922),
                ClassEvaluationResult(1028.0, 963.0, 65.0, 133.0, 0.937, 0.879, 0.907),
                ClassEvaluationResult(974.0, 892.0, 82.0, 363.0, 0.916, 0.711, 0.800),
                ClassEvaluationResult(1009.0, 801.0, 208.0, 62.0, 0.794, 0.928, 0.856),
        )

        assertThat(lrEvaluator.classesEvaluationResults.size).isEqualTo(expectedResults.size)
        lrEvaluator.classesEvaluationResults.forEach { (label, result) ->
            val expectedResult = expectedResults[Integer.parseInt(label)]
            assertThat(result.n).isEqualTo(expectedResult.n)
            assertThat(result.tp).isEqualTo(expectedResult.tp)
            assertThat(result.fn).isEqualTo(expectedResult.fn)
            assertThat(result.fp).isEqualTo(expectedResult.fp)
            assertThat(result.recall).isEqualTo(expectedResult.recall, OFFSET)
            assertThat(result.precision).isEqualTo(expectedResult.precision, OFFSET)
            assertThat(result.f1).isEqualTo(expectedResult.f1, OFFSET)
        }

        assertThat(lrEvaluator.accuracy).isEqualTo(0.891, OFFSET)
        assertThat(lrEvaluator.microAverageRecall).isEqualTo(0.891, OFFSET)
        assertThat(lrEvaluator.microAveragePrecision).isEqualTo(0.891, OFFSET)
        assertThat(lrEvaluator.microAverageF1).isEqualTo(0.891, OFFSET)
        assertThat(lrEvaluator.macroAverageRecall).isEqualTo(0.89, OFFSET)
        assertThat(lrEvaluator.macroAveragePrecision).isEqualTo(0.896, OFFSET)
        assertThat(lrEvaluator.macroAverageF1).isEqualTo(0.89, OFFSET)
        assertThat(lrEvaluator.balancedErrorRate).isEqualTo(0.11, OFFSET)
    }

    @Order(4)
    @Test
    fun lrEvaluatorConfusionMatrix() {
        val lrEvaluatorConfusionMatrix = configuration.lrEvaluatorConfusionMatrix()
        val expectedResult = arrayOf(
            doubleArrayOf(904.0, 0.0, 2.0, 3.0, 1.0, 20.0, 26.0, 4.0, 18.0, 2.0),
            doubleArrayOf(0.0, 1072.0, 7.0, 3.0, 0.0, 2.0, 6.0, 2.0, 43.0, 0.0),
            doubleArrayOf(3.0, 6.0, 856.0, 26.0, 5.0, 7.0, 39.0, 8.0, 80.0, 2.0),
            doubleArrayOf(1.0, 0.0, 13.0, 844.0, 2.0, 64.0, 7.0, 14.0, 62.0, 3.0),
            doubleArrayOf(0.0, 0.0, 7.0, 2.0, 888.0, 1.0, 22.0, 15.0, 20.0, 27.0),
            doubleArrayOf(9.0, 1.0, 1.0, 27.0, 6.0, 751.0, 18.0, 7.0, 68.0, 4.0),
            doubleArrayOf(3.0, 1.0, 2.0, 1.0, 1.0, 9.0, 938.0, 1.0, 2.0, 0.0),
            doubleArrayOf(1.0, 5.0, 18.0, 6.0, 4.0, 1.0, 0.0, 963.0, 9.0, 21.0),
            doubleArrayOf(1.0, 3.0, 6.0, 9.0, 9.0, 25.0, 20.0, 6.0, 892.0, 3.0),
            doubleArrayOf(3.0, 2.0, 0.0, 7.0, 44.0, 14.0, 1.0, 76.0, 61.0, 801.0),
        )
        assertThat(lrEvaluatorConfusionMatrix.matrix).isEqualTo(expectedResult)
    }

    @Order(5)
    @Test
    fun newEvaluator() {
        val newEvaluator = configuration.newEvaluator()
        val expectedResults = arrayOf(
                ClassEvaluationResult(980.0, 904.0, 76.0, 21.0, 0.922, 0.977, 0.949),
                ClassEvaluationResult(1135.0, 1072.0, 63.0, 18.0, 0.944, 0.983, 0.964),
                ClassEvaluationResult(1032.0, 856.0, 176.0, 56.0, 0.829, 0.939, 0.881),
                ClassEvaluationResult(1010.0, 844.0, 166.0, 84.0, 0.836, 0.909, 0.871),
                ClassEvaluationResult(982.0, 888.0, 94.0, 72.0, 0.904, 0.925, 0.915),
                ClassEvaluationResult(892.0, 751.0, 141.0, 143.0, 0.842, 0.840, 0.841),
                ClassEvaluationResult(958.0, 938.0, 20.0, 139.0, 0.979, 0.871, 0.922),
                ClassEvaluationResult(1028.0, 963.0, 65.0, 133.0, 0.937, 0.879, 0.907),
                ClassEvaluationResult(974.0, 892.0, 82.0, 363.0, 0.916, 0.711, 0.800),
                ClassEvaluationResult(1009.0, 801.0, 208.0, 62.0, 0.794, 0.928, 0.856),
        )

        assertThat(newEvaluator.classesEvaluationResults.size).isEqualTo(expectedResults.size)
        newEvaluator.classesEvaluationResults.forEach { (label, result) ->
            val expectedResult = expectedResults[Integer.parseInt(label)]
            assertThat(result.n).isEqualTo(expectedResult.n)
            assertThat(result.tp).isEqualTo(expectedResult.tp)
            assertThat(result.fn).isEqualTo(expectedResult.fn)
            assertThat(result.fp).isEqualTo(expectedResult.fp)
            assertThat(result.recall).isEqualTo(expectedResult.recall, OFFSET)
            assertThat(result.precision).isEqualTo(expectedResult.precision, OFFSET)
            assertThat(result.f1).isEqualTo(expectedResult.f1, OFFSET)
        }

        assertThat(newEvaluator.accuracy).isEqualTo(0.891, OFFSET)
        assertThat(newEvaluator.microAverageRecall).isEqualTo(0.891, OFFSET)
        assertThat(newEvaluator.microAveragePrecision).isEqualTo(0.891, OFFSET)
        assertThat(newEvaluator.microAverageF1).isEqualTo(0.891, OFFSET)
        assertThat(newEvaluator.macroAverageRecall).isEqualTo(0.89, OFFSET)
        assertThat(newEvaluator.macroAveragePrecision).isEqualTo(0.896, OFFSET)
        assertThat(newEvaluator.macroAverageF1).isEqualTo(0.89, OFFSET)
        assertThat(newEvaluator.balancedErrorRate).isEqualTo(0.11, OFFSET)
    }

    @Order(6)
    @Test
    fun newEvaluatorConfusionMatrix() {
        val newEvaluatorConfusionMatrix = configuration.newEvaluatorConfusionMatrix()
        val expectedResult = arrayOf(
            doubleArrayOf(904.0, 0.0, 2.0, 3.0, 1.0, 20.0, 26.0, 4.0, 18.0, 2.0),
            doubleArrayOf(0.0, 1072.0, 7.0, 3.0, 0.0, 2.0, 6.0, 2.0, 43.0, 0.0),
            doubleArrayOf(3.0, 6.0, 856.0, 26.0, 5.0, 7.0, 39.0, 8.0, 80.0, 2.0),
            doubleArrayOf(1.0, 0.0, 13.0, 844.0, 2.0, 64.0, 7.0, 14.0, 62.0, 3.0),
            doubleArrayOf(0.0, 0.0, 7.0, 2.0, 888.0, 1.0, 22.0, 15.0, 20.0, 27.0),
            doubleArrayOf(9.0, 1.0, 1.0, 27.0, 6.0, 751.0, 18.0, 7.0, 68.0, 4.0),
            doubleArrayOf(3.0, 1.0, 2.0, 1.0, 1.0, 9.0, 938.0, 1.0, 2.0, 0.0),
            doubleArrayOf(1.0, 5.0, 18.0, 6.0, 4.0, 1.0, 0.0, 963.0, 9.0, 21.0),
            doubleArrayOf(1.0, 3.0, 6.0, 9.0, 9.0, 25.0, 20.0, 6.0, 892.0, 3.0),
            doubleArrayOf(3.0, 2.0, 0.0, 7.0, 44.0, 14.0, 1.0, 76.0, 61.0, 801.0),
        )
        assertThat(newEvaluatorConfusionMatrix.matrix).isEqualTo(expectedResult)
    }

    @Order(7)
    @Test
    fun newEvaluatorProvenance() {
        val evaluationProvenance = configuration.newEvaluatorProvenance()
        val evaluationProvenanceWithoutCreationDates = evaluationProvenance.lines().filter {
            !it.contains("datasource-creation-time") && !it.contains("trained-at") && !it.contains("file-modified-time")
        }.joinToString(System.lineSeparator())
        assertThat(evaluationProvenanceWithoutCreationDates).isEqualTo("""
            EvaluationProvenance(
            	class-name = org.tribuo.provenance.EvaluationProvenance
            	model-provenance = LinearSGDModel(
            			class-name = org.tribuo.classification.sgd.linear.LinearSGDModel
            			dataset = MutableDataset(
            					class-name = org.tribuo.MutableDataset
            					datasource = IDXDataSource(
            							class-name = org.tribuo.datasource.IDXDataSource
            							outputPath = ${client.resolve(TRAIN_LABELS_IDX_1_FILE_NAME)}
            							outputFactory = LabelFactory(
            									class-name = org.tribuo.classification.LabelFactory
            								)
            							featuresPath = ${client.resolve(TRAIN_IMAGES_IDX_3_FILE_NAME)}
            							output-resource-hash = 3552534A0A558BBED6AED32B30C495CCA23D567EC52CAC8BE1A0730E8010255C
            							idx-feature-type = UBYTE
            							features-resource-hash = 440FCABF73CC546FA21475E81EA370265605F56BE210A4024D2CA8F203523609
            							host-short-name = DataSource
            						)
            					transformations = List[]
            					is-sequence = false
            					is-dense = false
            					num-examples = 60000
            					num-features = 717
            					num-outputs = 10
            					tribuo-version = 4.0.1
            				)
            			trainer = LinearSGDTrainer(
            					class-name = org.tribuo.classification.sgd.linear.LinearSGDTrainer
            					seed = 1
            					minibatchSize = 1
            					shuffle = true
            					epochs = 2
            					optimiser = AdaGrad(
            							class-name = org.tribuo.math.optimisers.AdaGrad
            							epsilon = 0.01
            							initialLearningRate = 0.5
            							initialValue = 0.0
            							host-short-name = StochasticGradientOptimiser
            						)
            					objective = LogMulticlass(
            							class-name = org.tribuo.classification.sgd.objectives.LogMulticlass
            							host-short-name = LabelObjective
            						)
            					loggingInterval = 10000
            					train-invocation-count = 0
            					is-sequence = false
            					host-short-name = Trainer
            				)
            			instance-values = Map{
            				reconfigured-model=true
            			}
            			tribuo-version = 4.0.1
            		)
            	dataset-provenance = MutableDataset(
            			class-name = org.tribuo.MutableDataset
            			datasource = IDXDataSource(
            					class-name = org.tribuo.datasource.IDXDataSource
            					outputPath = ${client.resolve(T10K_LABELS_IDX_1_FILE_NAME)}
            					outputFactory = LabelFactory(
            							class-name = org.tribuo.classification.LabelFactory
            						)
            					featuresPath = ${client.resolve(T10K_IMAGES_IDX_3_FILE_NAME)}
            					output-resource-hash = F7AE60F92E00EC6DEBD23A6088C31DBD2371ECA3FFA0DEFAEFB259924204AEC6
            					idx-feature-type = UBYTE
            					features-resource-hash = 8D422C7B0A1C1C79245A5BCF07FE86E33EEAFEE792B84584AEC276F5A2DBC4E6
            					host-short-name = DataSource
            				)
            			transformations = List[]
            			is-sequence = false
            			is-dense = false
            			num-examples = 10000
            			num-features = 668
            			num-outputs = 10
            			tribuo-version = 4.0.1
            		)
            	tribuo-version = 4.0.1
            )
        """.trimIndent())
    }

    @Order(8)
    @Test
    fun transformedEvaluator() {
        val transformedEvaluator = configuration.transformedEvaluator()
        val expectedResults = arrayOf(
                ClassEvaluationResult(980.0, 957.0, 23.0, 40.0, 0.977, 0.960, 0.968),
                ClassEvaluationResult(1135.0, 1109.0, 26.0, 36.0, 0.977, 0.969, 0.973),
                ClassEvaluationResult(1032.0, 940.0, 92.0, 90.0, 0.911, 0.913, 0.912),
                ClassEvaluationResult(1010.0, 927.0, 83.0, 141.0, 0.918, 0.868, 0.892),
                ClassEvaluationResult(982.0, 914.0, 68.0, 73.0, 0.931, 0.926, 0.928),
                ClassEvaluationResult(892.0, 813.0, 79.0, 183.0, 0.911, 0.816, 0.861),
                ClassEvaluationResult(958.0, 892.0, 66.0, 45.0, 0.931, 0.952, 0.941),
                ClassEvaluationResult(1028.0, 918.0, 110.0, 54.0, 0.893, 0.944, 0.918),
                ClassEvaluationResult(974.0, 753.0, 221.0, 60.0, 0.773, 0.926, 0.843),
                ClassEvaluationResult(1009.0, 926.0, 83.0, 129.0, 0.918, 0.878, 0.897),
        )

        assertThat(transformedEvaluator.classesEvaluationResults.size).isEqualTo(expectedResults.size)
        transformedEvaluator.classesEvaluationResults.forEach { (label, result) ->
            val expectedResult = expectedResults[Integer.parseInt(label)]
            assertThat(result.n).isEqualTo(expectedResult.n)
            assertThat(result.tp).isEqualTo(expectedResult.tp)
            assertThat(result.fn).isEqualTo(expectedResult.fn)
            assertThat(result.fp).isEqualTo(expectedResult.fp)
            assertThat(result.recall).isEqualTo(expectedResult.recall, OFFSET)
            assertThat(result.precision).isEqualTo(expectedResult.precision, OFFSET)
            assertThat(result.f1).isEqualTo(expectedResult.f1, OFFSET)
        }

        assertThat(transformedEvaluator.accuracy).isEqualTo(0.915, OFFSET)
        assertThat(transformedEvaluator.microAverageRecall).isEqualTo(0.915, OFFSET)
        assertThat(transformedEvaluator.microAveragePrecision).isEqualTo(0.915, OFFSET)
        assertThat(transformedEvaluator.microAverageF1).isEqualTo(0.915, OFFSET)
        assertThat(transformedEvaluator.macroAverageRecall).isEqualTo(0.914, OFFSET)
        assertThat(transformedEvaluator.macroAveragePrecision).isEqualTo(0.915, OFFSET)
        assertThat(transformedEvaluator.macroAverageF1).isEqualTo(0.913, OFFSET)
        assertThat(transformedEvaluator.balancedErrorRate).isEqualTo(0.086, OFFSET)
    }

    @Order(9)
    @Test
    fun transformedEvaluatorConfusionMatrix() {
        val transformedEvaluatorConfusionMatrix = configuration.transformedEvaluatorConfusionMatrix()
        val expectedValue = arrayOf(
            doubleArrayOf(957.0, 0.0, 1.0, 2.0, 1.0, 12.0, 4.0, 2.0, 1.0, 0.0),
            doubleArrayOf(0.0, 1109.0, 10.0, 3.0, 0.0, 2.0, 3.0, 2.0, 6.0, 0.0),
            doubleArrayOf(4.0, 9.0, 940.0, 18.0, 9.0, 7.0, 11.0, 11.0, 19.0, 4.0),
            doubleArrayOf(6.0, 0.0, 25.0, 927.0, 0.0, 26.0, 2.0, 7.0, 9.0, 8.0),
            doubleArrayOf(1.0, 1.0, 7.0, 4.0, 914.0, 0.0, 9.0, 7.0, 4.0, 35.0),
            doubleArrayOf(7.0, 1.0, 2.0, 30.0, 8.0, 813.0, 9.0, 3.0, 18.0, 1.0),
            doubleArrayOf(8.0, 2.0, 14.0, 3.0, 8.0, 27.0, 892.0, 2.0, 2.0, 0.0),
            doubleArrayOf(1.0, 7.0, 17.0, 19.0, 8.0, 1.0, 0.0, 918.0, 1.0, 56.0),
            doubleArrayOf(7.0, 9.0, 13.0, 46.0, 11.0, 93.0, 7.0, 10.0, 753.0, 25.0),
            doubleArrayOf(6.0, 7.0, 1.0, 16.0, 28.0, 15.0, 0.0, 10.0, 0.0, 926.0),
        )
        assertThat(transformedEvaluatorConfusionMatrix.matrix).isEqualTo(expectedValue)
    }

    @Order(10)
    @Test
    fun mnistTransformedLogisticConfig() {
        val mnistTransformedLogisticConfig = String(configuration.mnistTransformedLogisticConfig())
        assertThat(mnistTransformedLogisticConfig).contains("""
            "name" : "transformtrainer-0",
        """.trimIndent())
    }
}