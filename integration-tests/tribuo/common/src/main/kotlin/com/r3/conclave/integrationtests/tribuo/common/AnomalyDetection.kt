package com.r3.conclave.integrationtests.tribuo.common

import kotlinx.serialization.Serializable
import org.tribuo.Dataset
import org.tribuo.anomaly.Event
import org.tribuo.anomaly.evaluation.AnomalyEvaluation
import org.tribuo.anomaly.evaluation.AnomalyEvaluator
import org.tribuo.anomaly.example.AnomalyDataGenerator
import org.tribuo.anomaly.libsvm.LibSVMAnomalyTrainer
import org.tribuo.anomaly.libsvm.SVMAnomalyType
import org.tribuo.common.libsvm.KernelType
import org.tribuo.common.libsvm.LibSVMModel
import org.tribuo.common.libsvm.SVMParameters

/**
 * Common interface between client and enclave.
 * The client's implementation is responsible for sending
 * requests to the enclave.
 * The enclave's implementation is responsible for processing
 * those requests and returning the requested data.
 */
interface IAnomalyDetection {
    fun trainAndEvaluate(): AnomalyEvaluationResult
    fun confusionMatrix(): String
}

/**
 * Enclave implementation of the [AnomalyDetection] tutorial.
 * This class is responsible for executing the [AnomalyDetection] logic
 * requested by the client.
 * @param id unique instance id for the [Regression] tutorial.
 * @param size The number of points to sample for each dataset.
 * @param fractionAnomalous The fraction of anomalous data to generate.
 */
class AnomalyDetection(id: Int, size: Long, fractionAnomalous: Double) : IAnomalyDetection, TribuoObject(id) {
    private val eval = AnomalyEvaluator()
    private var trainer: LibSVMAnomalyTrainer
    private var data: Dataset<Event>
    private var test: Dataset<Event>
    private lateinit var testEvaluation: AnomalyEvaluation

    init {
        val pair = AnomalyDataGenerator.gaussianAnomaly(size, fractionAnomalous)
        data = pair.a
        test = pair.b

        val params = SVMParameters(SVMAnomalyType(SVMAnomalyType.SVMMode.ONE_CLASS), KernelType.RBF)
        params.gamma = 1.0
        params.setNu(0.1)
        trainer = LibSVMAnomalyTrainer(params)
    }

    /**
     * Train and evaluate SVM model.
     * @return test evaluation results.
     */
    override fun trainAndEvaluate(): AnomalyEvaluationResult {
        val model: LibSVMModel<Event> = trainer.train(data)
        testEvaluation = eval.evaluate(model, test)
        return AnomalyEvaluationResult(testEvaluation.truePositives, testEvaluation.falsePositives,
            testEvaluation.trueNegatives, testEvaluation.falseNegatives,
            testEvaluation.precision, testEvaluation.recall, testEvaluation.f1)
    }

    /**
     * @return confusion matrix
     */
    override fun confusionMatrix(): String {
        return testEvaluation.confusionString()
    }

    /**
     * Request for the initialization of the [AnomalyDetection] tutorial.
     * @param size The number of points to sample for each dataset.
     * @param fractionAnomalous The fraction of anomalous data to generate.
     */
    @Serializable
    class InitializeAnomalyDetection(private val size: Long, private val fractionAnomalous: Double) : TribuoTask() {
        /**
         * Initializes an [AnomalyDetection] instance in the enclave.
         * @return The unique id of the [AnomalyDetection] instance.
         */
        override fun execute(): ByteArray {
            return encode {
                AnomalyDetection(id.getAndIncrement(), size, fractionAnomalous)
            }
        }
    }

    /**
     * Request training and evaluation of SVM model.
     * @param id unique instance id for the [AnomalyDetection] tutorial.
     */
    @Serializable
    class TrainAndEvaluate(private val id: Int) : TribuoTask() {
        /**
         * Train and evaluate SVM model.
         * @return serialized test evaluation results.
         */
        override fun execute(): ByteArray {
            return encode(id) { anomalyDetection: AnomalyDetection ->
                anomalyDetection.trainAndEvaluate()
            }
        }
    }

    /**
     * Request confusion matrix.
     * @param id unique instance id for the [Regression] tutorial.
     */
    @Serializable
    class ConfusionMatrix(private val id: Int) : TribuoTask() {
        /**
         * Obtains the confusion matrix.
         * @return serialized confusion matrix.
         */
        override fun execute(): ByteArray {
            return encode(id) { anomalyDetection: AnomalyDetection ->
                anomalyDetection.confusionMatrix()
            }
        }
    }
}

@Serializable
data class AnomalyEvaluationResult(val truePositives: Long, val falsePositives: Long,
                                   val trueNegatives: Long, val falseNegatives: Long,
                                   val precision: Double, val recall: Double, val f1: Double)
