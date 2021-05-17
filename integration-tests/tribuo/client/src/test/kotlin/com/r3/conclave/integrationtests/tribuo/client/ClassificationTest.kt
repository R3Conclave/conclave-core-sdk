package com.r3.conclave.integrationtests.tribuo.client

import com.r3.conclave.integrationtests.tribuo.common.ClassEvaluationResult
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.data.Offset
import org.junit.jupiter.api.*
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.ExtensionContext
import org.junit.jupiter.api.extension.TestWatcher
import org.tribuo.classification.Label

internal class ClassificationTestWatcher : TestWatcher {
    override fun testFailed(context: ExtensionContext?, cause: Throwable?) {
        ClassificationTest.classificationTeardown()
    }
}

@TestMethodOrder(MethodOrderer.OrderAnnotation::class)
@ExtendWith(ClassificationTestWatcher::class)
class ClassificationTest : TribuoTest() {
    companion object {
        private lateinit var classification: Classification
        private lateinit var enclaveIrisDataPath: String

        @BeforeAll
        @JvmStatic
        fun classificationSetup() {
            classification = Classification(client)
            enclaveIrisDataPath = classification.irisDataPath
        }

        @AfterAll
        @JvmStatic
        fun classificationTeardown() {
            classification.close()
        }
    }

    @Order(0)
    @Test
    fun dataStats() {
        val dataStats = classification.dataStats()
        assertThat(dataStats.trainingDataset.size()).isEqualTo(105)
        assertThat(dataStats.trainingDataset.featureMap.size()).isEqualTo(4)
        assertThat(dataStats.trainingDataset.outputInfo.size()).isEqualTo(3)
        assertThat(dataStats.testingDataset.size()).isEqualTo(45)
        assertThat(dataStats.testingDataset.featureMap.size()).isEqualTo(4)
        assertThat(dataStats.testingDataset.outputInfo.size()).isEqualTo(3)
        val expectedLabels = arrayOf(
                Label("Iris-versicolor"),
                Label("Iris-virginica"),
                Label("Iris-setosa")
        )
        assertThat(dataStats.trainingDataset.outputInfo.domain).containsExactlyInAnyOrder(*expectedLabels)
        assertThat(dataStats.testingDataset.outputInfo.domain).containsExactlyInAnyOrder(*expectedLabels)
    }

    @Order(1)
    @Test
    fun trainerInfo() {
        val trainerInfo = classification.trainerInfo()
        assertThat(trainerInfo)
                .isEqualTo("LinearSGDTrainer(objective=LogMulticlass,optimiser=AdaGrad(initialLearningRate=1.0,epsilon=0.1,initialValue=0.0),epochs=5,minibatchSize=1,seed=12345)")
    }

    @Order(2)
    @Test
    fun trainAndEvaluate() {
        val evaluation = classification.trainAndEvaluate()
        val expectedResults = mapOf(
                "Iris-versicolor" to ClassEvaluationResult(16.0, 16.0, 0.0, 1.0, 1.0, 0.941, 0.97),
                "Iris-virginica" to ClassEvaluationResult(15.0, 14.0, 1.0, 0.0, 0.933, 1.0, 0.966),
                "Iris-setosa" to ClassEvaluationResult(14.0, 14.0, 0.0, 0.0, 1.0, 1.0, 1.0),
        )
        val offset = Offset.offset(0.001)
        assertThat(evaluation.classesEvaluationResults.size).isEqualTo(expectedResults.size)
        evaluation.classesEvaluationResults.forEach { (label, result) ->
            val expected = expectedResults[label]!!
            assertThat(result.n).isEqualTo(expected.n)
            assertThat(result.tp).isEqualTo(expected.tp)
            assertThat(result.fn).isEqualTo(expected.fn)
            assertThat(result.fp).isEqualTo(expected.fp)
            assertThat(result.recall).isEqualTo(expected.recall, offset)
            assertThat(result.precision).isEqualTo(expected.precision, offset)
            assertThat(result.f1).isEqualTo(expected.f1, offset)
        }
        assertThat(evaluation.accuracy).isEqualTo(0.978, offset)

        assertThat(evaluation.microAverageRecall).isEqualTo(0.978, offset)
        assertThat(evaluation.microAveragePrecision).isEqualTo(0.978, offset)
        assertThat(evaluation.microAverageF1).isEqualTo(0.978, offset)

        assertThat(evaluation.macroAverageRecall).isEqualTo(0.978, offset)
        assertThat(evaluation.macroAveragePrecision).isEqualTo(0.98, offset)
        assertThat(evaluation.macroAverageF1).isEqualTo(0.978, offset)

        assertThat(evaluation.balancedErrorRate).isEqualTo(0.022, offset)
    }

    @Order(3)
    @Test
    fun confusionMatrix() {
        val confusionMatrix = classification.confusionMatrix()
        assertThat(confusionMatrix.labels).containsExactly(
                "Iris-versicolor",
                "Iris-virginica",
                "Iris-setosa"
        )
        assertThat(confusionMatrix.matrix).isEqualTo(arrayOf(
                arrayOf(16.0, 0.0, 0.0),
                arrayOf(1.0, 14.0, 0.0),
                arrayOf(0.0, 0.0, 14.0),
        ))
    }

    @Order(4)
    @Test
    fun serializedModel() {
        val serializedModel = classification.serializedModel()
        val loadedModel = classification.loadModel(serializedModel)
        assertThat(loadedModel).isEqualTo("It's a Model<Label>!")
    }

    @Order(5)
    @Test
    fun modelMetadata() {
        val modelMetadata = classification.modelMetadata()
        assertThat(modelMetadata).isEqualTo("""
            CategoricalFeature(name=petalLength,id=0,count=105,map={1.2=1, 6.9=1, 3.6=1, 3.0=1, 1.7=4, 4.9=4, 4.4=3, 3.5=2, 5.9=2, 5.4=1, 4.0=4, 1.4=12, 4.5=4, 5.0=2, 5.5=3, 6.7=2, 3.7=1, 1.9=1, 6.0=2, 5.2=1, 5.7=2, 4.2=2, 4.7=2, 4.8=4, 1.6=4, 5.8=2, 3.8=1, 6.3=1, 3.3=1, 1.0=1, 5.6=4, 5.1=5, 4.6=3, 4.1=2, 1.5=9, 1.3=4, 3.9=3, 6.6=1, 6.1=2})
            CategoricalFeature(name=petalWidth,id=1,count=105,map={2.0=3, 0.5=1, 1.2=3, 0.3=6, 1.6=2, 0.1=3, 0.4=5, 2.5=3, 2.3=4, 1.7=2, 1.1=3, 2.1=4, 0.6=1, 1.4=6, 1.0=5, 2.4=1, 1.8=12, 0.2=20, 1.9=4, 1.5=7, 1.3=8, 2.2=2})
            CategoricalFeature(name=sepalLength,id=2,count=105,map={6.9=3, 6.4=3, 7.4=1, 4.9=4, 4.4=1, 5.9=3, 5.4=5, 7.2=3, 7.7=3, 5.0=8, 6.2=2, 5.5=5, 6.7=7, 6.0=3, 5.2=2, 6.5=3, 5.7=4, 4.7=2, 4.8=3, 5.8=4, 5.3=1, 6.8=3, 6.3=5, 7.3=1, 5.6=6, 5.1=7, 4.6=4, 7.6=1, 7.1=1, 6.6=2, 6.1=5})
            CategoricalFeature(name=sepalWidth,id=3,count=105,map={2.0=1, 2.8=10, 3.6=4, 2.3=3, 2.5=5, 3.1=8, 3.8=4, 3.0=19, 2.6=4, 4.4=1, 3.3=4, 3.5=4, 2.4=2, 3.2=10, 2.9=5, 3.7=3, 3.4=6, 2.2=2, 3.9=2, 4.2=1, 2.7=7})
        """.trimIndent())
    }

    @Order(6)
    @Test
    fun modelProvenance() {
        val modelProvenance = classification.modelProvenance()
        assertThat(modelProvenance.lines()
                .filter { !it.contains("file-modified-time") }
                .joinToString(System.lineSeparator())
                .replace("\t","    ")).isEqualTo("""
            TrainTestSplitter(
                class-name = org.tribuo.evaluation.TrainTestSplitter
                source = CSVLoader(
                        class-name = org.tribuo.data.csv.CSVLoader
                        outputFactory = LabelFactory(
                                class-name = org.tribuo.classification.LabelFactory
                            )
                        response-name = species
                        separator = ,
                        quote = "
                        path = file:$enclaveIrisDataPath
                        resource-hash = 0FED2A99DB77EC533A62DC66894D3EC6DF3B58B6A8F3CF4A6B47E4086B7F97DC
                    )
                train-proportion = 0.7
                seed = 1
                size = 150
                is-train = true
            )
            LogisticRegressionTrainer(
                class-name = org.tribuo.classification.sgd.linear.LogisticRegressionTrainer
                seed = 12345
                minibatchSize = 1
                shuffle = true
                epochs = 5
                optimiser = AdaGrad(
                        class-name = org.tribuo.math.optimisers.AdaGrad
                        epsilon = 0.1
                        initialLearningRate = 1.0
                        initialValue = 0.0
                        host-short-name = StochasticGradientOptimiser
                    )
                objective = LogMulticlass(
                        class-name = org.tribuo.classification.sgd.objectives.LogMulticlass
                        host-short-name = LabelObjective
                    )
                loggingInterval = 1000
                train-invocation-count = 0
                is-sequence = false
                host-short-name = Trainer
            )
        """.trimIndent())
    }

    @Order(7)
    @Test
    fun jsonProvenance() {
        val jsonProvenance = classification.jsonProvenance()
        val jsonElement = Json.parseToJsonElement(jsonProvenance)
        val expectedJsonElement = Json.parseToJsonElement("""
            [ {
              "marshalled-class" : "com.oracle.labs.mlrg.olcut.provenance.io.ObjectMarshalledProvenance",
              "object-name" : "linearsgdmodel-0",
              "object-class-name" : "org.tribuo.classification.sgd.linear.LinearSGDModel",
              "provenance-class" : "org.tribuo.provenance.ModelProvenance",
              "map" : {
                "instance-values" : {
                  "marshalled-class" : "com.oracle.labs.mlrg.olcut.provenance.io.MapMarshalledProvenance",
                  "map" : { }
                },
                "tribuo-version" : {
                  "marshalled-class" : "com.oracle.labs.mlrg.olcut.provenance.io.SimpleMarshalledProvenance",
                  "key" : "tribuo-version",
                  "value" : "4.0.1",
                  "provenance-class" : "com.oracle.labs.mlrg.olcut.provenance.primitives.StringProvenance",
                  "additional" : "",
                  "is-reference" : false
                },
                "trainer" : {
                  "marshalled-class" : "com.oracle.labs.mlrg.olcut.provenance.io.SimpleMarshalledProvenance",
                  "key" : "trainer",
                  "value" : "logisticregressiontrainer-2",
                  "provenance-class" : "org.tribuo.provenance.impl.TrainerProvenanceImpl",
                  "additional" : "",
                  "is-reference" : true
                },
                "trained-at" : {
                  "marshalled-class" : "com.oracle.labs.mlrg.olcut.provenance.io.SimpleMarshalledProvenance",
                  "key" : "trained-at",
                  "value" : "1970-01-01T00:00:00Z",
                  "provenance-class" : "com.oracle.labs.mlrg.olcut.provenance.primitives.DateTimeProvenance",
                  "additional" : "",
                  "is-reference" : false
                },
                "dataset" : {
                  "marshalled-class" : "com.oracle.labs.mlrg.olcut.provenance.io.SimpleMarshalledProvenance",
                  "key" : "dataset",
                  "value" : "mutabledataset-1",
                  "provenance-class" : "org.tribuo.provenance.DatasetProvenance",
                  "additional" : "",
                  "is-reference" : true
                },
                "class-name" : {
                  "marshalled-class" : "com.oracle.labs.mlrg.olcut.provenance.io.SimpleMarshalledProvenance",
                  "key" : "class-name",
                  "value" : "org.tribuo.classification.sgd.linear.LinearSGDModel",
                  "provenance-class" : "com.oracle.labs.mlrg.olcut.provenance.primitives.StringProvenance",
                  "additional" : "",
                  "is-reference" : false
                }
              }
            }, {
              "marshalled-class" : "com.oracle.labs.mlrg.olcut.provenance.io.ObjectMarshalledProvenance",
              "object-name" : "mutabledataset-1",
              "object-class-name" : "org.tribuo.MutableDataset",
              "provenance-class" : "org.tribuo.provenance.DatasetProvenance",
              "map" : {
                "num-features" : {
                  "marshalled-class" : "com.oracle.labs.mlrg.olcut.provenance.io.SimpleMarshalledProvenance",
                  "key" : "num-features",
                  "value" : "4",
                  "provenance-class" : "com.oracle.labs.mlrg.olcut.provenance.primitives.IntProvenance",
                  "additional" : "",
                  "is-reference" : false
                },
                "num-examples" : {
                  "marshalled-class" : "com.oracle.labs.mlrg.olcut.provenance.io.SimpleMarshalledProvenance",
                  "key" : "num-examples",
                  "value" : "105",
                  "provenance-class" : "com.oracle.labs.mlrg.olcut.provenance.primitives.IntProvenance",
                  "additional" : "",
                  "is-reference" : false
                },
                "num-outputs" : {
                  "marshalled-class" : "com.oracle.labs.mlrg.olcut.provenance.io.SimpleMarshalledProvenance",
                  "key" : "num-outputs",
                  "value" : "3",
                  "provenance-class" : "com.oracle.labs.mlrg.olcut.provenance.primitives.IntProvenance",
                  "additional" : "",
                  "is-reference" : false
                },
                "tribuo-version" : {
                  "marshalled-class" : "com.oracle.labs.mlrg.olcut.provenance.io.SimpleMarshalledProvenance",
                  "key" : "tribuo-version",
                  "value" : "4.0.1",
                  "provenance-class" : "com.oracle.labs.mlrg.olcut.provenance.primitives.StringProvenance",
                  "additional" : "",
                  "is-reference" : false
                },
                "datasource" : {
                  "marshalled-class" : "com.oracle.labs.mlrg.olcut.provenance.io.SimpleMarshalledProvenance",
                  "key" : "datasource",
                  "value" : "traintestsplitter-3",
                  "provenance-class" : "org.tribuo.evaluation.TrainTestSplitter${"$"}SplitDataSourceProvenance",
                  "additional" : "",
                  "is-reference" : true
                },
                "transformations" : {
                  "marshalled-class" : "com.oracle.labs.mlrg.olcut.provenance.io.ListMarshalledProvenance",
                  "list" : [ ]
                },
                "is-sequence" : {
                  "marshalled-class" : "com.oracle.labs.mlrg.olcut.provenance.io.SimpleMarshalledProvenance",
                  "key" : "is-sequence",
                  "value" : "false",
                  "provenance-class" : "com.oracle.labs.mlrg.olcut.provenance.primitives.BooleanProvenance",
                  "additional" : "",
                  "is-reference" : false
                },
                "is-dense" : {
                  "marshalled-class" : "com.oracle.labs.mlrg.olcut.provenance.io.SimpleMarshalledProvenance",
                  "key" : "is-dense",
                  "value" : "false",
                  "provenance-class" : "com.oracle.labs.mlrg.olcut.provenance.primitives.BooleanProvenance",
                  "additional" : "",
                  "is-reference" : false
                },
                "class-name" : {
                  "marshalled-class" : "com.oracle.labs.mlrg.olcut.provenance.io.SimpleMarshalledProvenance",
                  "key" : "class-name",
                  "value" : "org.tribuo.MutableDataset",
                  "provenance-class" : "com.oracle.labs.mlrg.olcut.provenance.primitives.StringProvenance",
                  "additional" : "",
                  "is-reference" : false
                }
              }
            }, {
              "marshalled-class" : "com.oracle.labs.mlrg.olcut.provenance.io.ObjectMarshalledProvenance",
              "object-name" : "logisticregressiontrainer-2",
              "object-class-name" : "org.tribuo.classification.sgd.linear.LogisticRegressionTrainer",
              "provenance-class" : "org.tribuo.provenance.impl.TrainerProvenanceImpl",
              "map" : {
                "seed" : {
                  "marshalled-class" : "com.oracle.labs.mlrg.olcut.provenance.io.SimpleMarshalledProvenance",
                  "key" : "seed",
                  "value" : "12345",
                  "provenance-class" : "com.oracle.labs.mlrg.olcut.provenance.primitives.LongProvenance",
                  "additional" : "",
                  "is-reference" : false
                },
                "minibatchSize" : {
                  "marshalled-class" : "com.oracle.labs.mlrg.olcut.provenance.io.SimpleMarshalledProvenance",
                  "key" : "minibatchSize",
                  "value" : "1",
                  "provenance-class" : "com.oracle.labs.mlrg.olcut.provenance.primitives.IntProvenance",
                  "additional" : "",
                  "is-reference" : false
                },
                "train-invocation-count" : {
                  "marshalled-class" : "com.oracle.labs.mlrg.olcut.provenance.io.SimpleMarshalledProvenance",
                  "key" : "train-invocation-count",
                  "value" : "0",
                  "provenance-class" : "com.oracle.labs.mlrg.olcut.provenance.primitives.IntProvenance",
                  "additional" : "",
                  "is-reference" : false
                },
                "is-sequence" : {
                  "marshalled-class" : "com.oracle.labs.mlrg.olcut.provenance.io.SimpleMarshalledProvenance",
                  "key" : "is-sequence",
                  "value" : "false",
                  "provenance-class" : "com.oracle.labs.mlrg.olcut.provenance.primitives.BooleanProvenance",
                  "additional" : "",
                  "is-reference" : false
                },
                "shuffle" : {
                  "marshalled-class" : "com.oracle.labs.mlrg.olcut.provenance.io.SimpleMarshalledProvenance",
                  "key" : "shuffle",
                  "value" : "true",
                  "provenance-class" : "com.oracle.labs.mlrg.olcut.provenance.primitives.BooleanProvenance",
                  "additional" : "",
                  "is-reference" : false
                },
                "epochs" : {
                  "marshalled-class" : "com.oracle.labs.mlrg.olcut.provenance.io.SimpleMarshalledProvenance",
                  "key" : "epochs",
                  "value" : "5",
                  "provenance-class" : "com.oracle.labs.mlrg.olcut.provenance.primitives.IntProvenance",
                  "additional" : "",
                  "is-reference" : false
                },
                "optimiser" : {
                  "marshalled-class" : "com.oracle.labs.mlrg.olcut.provenance.io.SimpleMarshalledProvenance",
                  "key" : "optimiser",
                  "value" : "adagrad-4",
                  "provenance-class" : "com.oracle.labs.mlrg.olcut.provenance.impl.ConfiguredObjectProvenanceImpl",
                  "additional" : "",
                  "is-reference" : true
                },
                "host-short-name" : {
                  "marshalled-class" : "com.oracle.labs.mlrg.olcut.provenance.io.SimpleMarshalledProvenance",
                  "key" : "host-short-name",
                  "value" : "Trainer",
                  "provenance-class" : "com.oracle.labs.mlrg.olcut.provenance.primitives.StringProvenance",
                  "additional" : "",
                  "is-reference" : false
                },
                "class-name" : {
                  "marshalled-class" : "com.oracle.labs.mlrg.olcut.provenance.io.SimpleMarshalledProvenance",
                  "key" : "class-name",
                  "value" : "org.tribuo.classification.sgd.linear.LogisticRegressionTrainer",
                  "provenance-class" : "com.oracle.labs.mlrg.olcut.provenance.primitives.StringProvenance",
                  "additional" : "",
                  "is-reference" : false
                },
                "objective" : {
                  "marshalled-class" : "com.oracle.labs.mlrg.olcut.provenance.io.SimpleMarshalledProvenance",
                  "key" : "objective",
                  "value" : "logmulticlass-5",
                  "provenance-class" : "com.oracle.labs.mlrg.olcut.provenance.impl.ConfiguredObjectProvenanceImpl",
                  "additional" : "",
                  "is-reference" : true
                },
                "loggingInterval" : {
                  "marshalled-class" : "com.oracle.labs.mlrg.olcut.provenance.io.SimpleMarshalledProvenance",
                  "key" : "loggingInterval",
                  "value" : "1000",
                  "provenance-class" : "com.oracle.labs.mlrg.olcut.provenance.primitives.IntProvenance",
                  "additional" : "",
                  "is-reference" : false
                }
              }
            }, {
              "marshalled-class" : "com.oracle.labs.mlrg.olcut.provenance.io.ObjectMarshalledProvenance",
              "object-name" : "traintestsplitter-3",
              "object-class-name" : "org.tribuo.evaluation.TrainTestSplitter",
              "provenance-class" : "org.tribuo.evaluation.TrainTestSplitter${"$"}SplitDataSourceProvenance",
              "map" : {
                "train-proportion" : {
                  "marshalled-class" : "com.oracle.labs.mlrg.olcut.provenance.io.SimpleMarshalledProvenance",
                  "key" : "train-proportion",
                  "value" : "0.7",
                  "provenance-class" : "com.oracle.labs.mlrg.olcut.provenance.primitives.DoubleProvenance",
                  "additional" : "",
                  "is-reference" : false
                },
                "seed" : {
                  "marshalled-class" : "com.oracle.labs.mlrg.olcut.provenance.io.SimpleMarshalledProvenance",
                  "key" : "seed",
                  "value" : "1",
                  "provenance-class" : "com.oracle.labs.mlrg.olcut.provenance.primitives.LongProvenance",
                  "additional" : "",
                  "is-reference" : false
                },
                "size" : {
                  "marshalled-class" : "com.oracle.labs.mlrg.olcut.provenance.io.SimpleMarshalledProvenance",
                  "key" : "size",
                  "value" : "150",
                  "provenance-class" : "com.oracle.labs.mlrg.olcut.provenance.primitives.IntProvenance",
                  "additional" : "",
                  "is-reference" : false
                },
                "source" : {
                  "marshalled-class" : "com.oracle.labs.mlrg.olcut.provenance.io.SimpleMarshalledProvenance",
                  "key" : "source",
                  "value" : "csvloader-6",
                  "provenance-class" : "org.tribuo.data.csv.CSVLoader${"$"}CSVLoaderProvenance",
                  "additional" : "",
                  "is-reference" : true
                },
                "class-name" : {
                  "marshalled-class" : "com.oracle.labs.mlrg.olcut.provenance.io.SimpleMarshalledProvenance",
                  "key" : "class-name",
                  "value" : "org.tribuo.evaluation.TrainTestSplitter",
                  "provenance-class" : "com.oracle.labs.mlrg.olcut.provenance.primitives.StringProvenance",
                  "additional" : "",
                  "is-reference" : false
                },
                "is-train" : {
                  "marshalled-class" : "com.oracle.labs.mlrg.olcut.provenance.io.SimpleMarshalledProvenance",
                  "key" : "is-train",
                  "value" : "true",
                  "provenance-class" : "com.oracle.labs.mlrg.olcut.provenance.primitives.BooleanProvenance",
                  "additional" : "",
                  "is-reference" : false
                }
              }
            }, {
              "marshalled-class" : "com.oracle.labs.mlrg.olcut.provenance.io.ObjectMarshalledProvenance",
              "object-name" : "adagrad-4",
              "object-class-name" : "org.tribuo.math.optimisers.AdaGrad",
              "provenance-class" : "com.oracle.labs.mlrg.olcut.provenance.impl.ConfiguredObjectProvenanceImpl",
              "map" : {
                "epsilon" : {
                  "marshalled-class" : "com.oracle.labs.mlrg.olcut.provenance.io.SimpleMarshalledProvenance",
                  "key" : "epsilon",
                  "value" : "0.1",
                  "provenance-class" : "com.oracle.labs.mlrg.olcut.provenance.primitives.DoubleProvenance",
                  "additional" : "",
                  "is-reference" : false
                },
                "initialLearningRate" : {
                  "marshalled-class" : "com.oracle.labs.mlrg.olcut.provenance.io.SimpleMarshalledProvenance",
                  "key" : "initialLearningRate",
                  "value" : "1.0",
                  "provenance-class" : "com.oracle.labs.mlrg.olcut.provenance.primitives.DoubleProvenance",
                  "additional" : "",
                  "is-reference" : false
                },
                "initialValue" : {
                  "marshalled-class" : "com.oracle.labs.mlrg.olcut.provenance.io.SimpleMarshalledProvenance",
                  "key" : "initialValue",
                  "value" : "0.0",
                  "provenance-class" : "com.oracle.labs.mlrg.olcut.provenance.primitives.DoubleProvenance",
                  "additional" : "",
                  "is-reference" : false
                },
                "host-short-name" : {
                  "marshalled-class" : "com.oracle.labs.mlrg.olcut.provenance.io.SimpleMarshalledProvenance",
                  "key" : "host-short-name",
                  "value" : "StochasticGradientOptimiser",
                  "provenance-class" : "com.oracle.labs.mlrg.olcut.provenance.primitives.StringProvenance",
                  "additional" : "",
                  "is-reference" : false
                },
                "class-name" : {
                  "marshalled-class" : "com.oracle.labs.mlrg.olcut.provenance.io.SimpleMarshalledProvenance",
                  "key" : "class-name",
                  "value" : "org.tribuo.math.optimisers.AdaGrad",
                  "provenance-class" : "com.oracle.labs.mlrg.olcut.provenance.primitives.StringProvenance",
                  "additional" : "",
                  "is-reference" : false
                }
              }
            }, {
              "marshalled-class" : "com.oracle.labs.mlrg.olcut.provenance.io.ObjectMarshalledProvenance",
              "object-name" : "logmulticlass-5",
              "object-class-name" : "org.tribuo.classification.sgd.objectives.LogMulticlass",
              "provenance-class" : "com.oracle.labs.mlrg.olcut.provenance.impl.ConfiguredObjectProvenanceImpl",
              "map" : {
                "host-short-name" : {
                  "marshalled-class" : "com.oracle.labs.mlrg.olcut.provenance.io.SimpleMarshalledProvenance",
                  "key" : "host-short-name",
                  "value" : "LabelObjective",
                  "provenance-class" : "com.oracle.labs.mlrg.olcut.provenance.primitives.StringProvenance",
                  "additional" : "",
                  "is-reference" : false
                },
                "class-name" : {
                  "marshalled-class" : "com.oracle.labs.mlrg.olcut.provenance.io.SimpleMarshalledProvenance",
                  "key" : "class-name",
                  "value" : "org.tribuo.classification.sgd.objectives.LogMulticlass",
                  "provenance-class" : "com.oracle.labs.mlrg.olcut.provenance.primitives.StringProvenance",
                  "additional" : "",
                  "is-reference" : false
                }
              }
            }, {
              "marshalled-class" : "com.oracle.labs.mlrg.olcut.provenance.io.ObjectMarshalledProvenance",
              "object-name" : "csvloader-6",
              "object-class-name" : "org.tribuo.data.csv.CSVLoader",
              "provenance-class" : "org.tribuo.data.csv.CSVLoader${"$"}CSVLoaderProvenance",
              "map" : {
                "resource-hash" : {
                  "marshalled-class" : "com.oracle.labs.mlrg.olcut.provenance.io.SimpleMarshalledProvenance",
                  "key" : "resource-hash",
                  "value" : "0FED2A99DB77EC533A62DC66894D3EC6DF3B58B6A8F3CF4A6B47E4086B7F97DC",
                  "provenance-class" : "com.oracle.labs.mlrg.olcut.provenance.primitives.HashProvenance",
                  "additional" : "SHA256",
                  "is-reference" : false
                },
                "path" : {
                  "marshalled-class" : "com.oracle.labs.mlrg.olcut.provenance.io.SimpleMarshalledProvenance",
                  "key" : "path",
                  "value" : "file:$enclaveIrisDataPath",
                  "provenance-class" : "com.oracle.labs.mlrg.olcut.provenance.primitives.URLProvenance",
                  "additional" : "",
                  "is-reference" : false
                },
                "file-modified-time" : {
                  "marshalled-class" : "com.oracle.labs.mlrg.olcut.provenance.io.SimpleMarshalledProvenance",
                  "key" : "file-modified-time",
                  "value" : "1970-01-01T00:00:00Z",
                  "provenance-class" : "com.oracle.labs.mlrg.olcut.provenance.primitives.DateTimeProvenance",
                  "additional" : "",
                  "is-reference" : false
                },
                "quote" : {
                  "marshalled-class" : "com.oracle.labs.mlrg.olcut.provenance.io.SimpleMarshalledProvenance",
                  "key" : "quote",
                  "value" : "\"",
                  "provenance-class" : "com.oracle.labs.mlrg.olcut.provenance.primitives.CharProvenance",
                  "additional" : "",
                  "is-reference" : false
                },
                "response-name" : {
                  "marshalled-class" : "com.oracle.labs.mlrg.olcut.provenance.io.SimpleMarshalledProvenance",
                  "key" : "response-name",
                  "value" : "species",
                  "provenance-class" : "com.oracle.labs.mlrg.olcut.provenance.primitives.StringProvenance",
                  "additional" : "",
                  "is-reference" : false
                },
                "outputFactory" : {
                  "marshalled-class" : "com.oracle.labs.mlrg.olcut.provenance.io.SimpleMarshalledProvenance",
                  "key" : "outputFactory",
                  "value" : "labelfactory-7",
                  "provenance-class" : "org.tribuo.classification.LabelFactory${"$"}LabelFactoryProvenance",
                  "additional" : "",
                  "is-reference" : true
                },
                "separator" : {
                  "marshalled-class" : "com.oracle.labs.mlrg.olcut.provenance.io.SimpleMarshalledProvenance",
                  "key" : "separator",
                  "value" : ",",
                  "provenance-class" : "com.oracle.labs.mlrg.olcut.provenance.primitives.CharProvenance",
                  "additional" : "",
                  "is-reference" : false
                },
                "class-name" : {
                  "marshalled-class" : "com.oracle.labs.mlrg.olcut.provenance.io.SimpleMarshalledProvenance",
                  "key" : "class-name",
                  "value" : "org.tribuo.data.csv.CSVLoader",
                  "provenance-class" : "com.oracle.labs.mlrg.olcut.provenance.primitives.StringProvenance",
                  "additional" : "",
                  "is-reference" : false
                }
              }
            }, {
              "marshalled-class" : "com.oracle.labs.mlrg.olcut.provenance.io.ObjectMarshalledProvenance",
              "object-name" : "labelfactory-7",
              "object-class-name" : "org.tribuo.classification.LabelFactory",
              "provenance-class" : "org.tribuo.classification.LabelFactory${"$"}LabelFactoryProvenance",
              "map" : {
                "class-name" : {
                  "marshalled-class" : "com.oracle.labs.mlrg.olcut.provenance.io.SimpleMarshalledProvenance",
                  "key" : "class-name",
                  "value" : "org.tribuo.classification.LabelFactory",
                  "provenance-class" : "com.oracle.labs.mlrg.olcut.provenance.primitives.StringProvenance",
                  "additional" : "",
                  "is-reference" : false
                }
              }
            } ]
        """.trimIndent())

        for (i in 0 until jsonElement.jsonArray.size) {
            val element = jsonElement.jsonArray[i].jsonObject
            val expectedElement = expectedJsonElement.jsonArray[i].jsonObject
            assertThat(element.entries.filter { it.key != "map" }).isEqualTo(expectedElement.entries.filter { it.key != "map" })
            // compare map
            val mapElement = element["map"]?.jsonObject
            val expectedMapElement = expectedElement["map"]?.jsonObject
            assertThat(mapElement?.entries?.filter { !it.key.contains("trained-at") && !it.key.contains("file-modified-time") })
                    .isEqualTo(expectedMapElement?.entries?.filter { !it.key.contains("trained-at") && !it.key.contains("file-modified-time") })
            // compare trained-at excluding timestamp value
            assertThat(mapElement?.get("trained-at")?.jsonObject?.filter { !it.key.contains("value") })
                    .isEqualTo(expectedMapElement?.get("trained-at")?.jsonObject?.filter { !it.key.contains("value") })
            // compare file-modified-time excluding timestamp value
            assertThat(mapElement?.get("file-modified-time")?.jsonObject?.filter { !it.key.contains("value") })
                    .isEqualTo(expectedMapElement?.get("file-modified-time")?.jsonObject?.filter { !it.key.contains("value") })
        }
    }

}