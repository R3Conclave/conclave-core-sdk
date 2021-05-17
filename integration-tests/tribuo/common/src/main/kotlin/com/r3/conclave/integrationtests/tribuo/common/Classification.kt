package com.r3.conclave.integrationtests.tribuo.common

import com.r3.conclave.integrationtests.tribuo.common.TribuoObject.Companion.encode
import com.r3.conclave.integrationtests.tribuo.common.TribuoObject.Companion.id
import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.tribuo.Dataset
import org.tribuo.Model
import org.tribuo.MutableDataset
import org.tribuo.Trainer
import org.tribuo.classification.Label
import org.tribuo.classification.LabelFactory
import org.tribuo.classification.evaluation.LabelEvaluation
import org.tribuo.classification.evaluation.LabelEvaluator
import org.tribuo.classification.sgd.linear.LogisticRegressionTrainer
import org.tribuo.data.csv.CSVLoader
import org.tribuo.evaluation.TrainTestSplitter
import java.nio.file.Paths

/**
 * Common interface between client and enclave.
 * The client's implementation is responsible for sending
 * requests to the enclave.
 * The enclave's implementation is responsible for processing
 * those requests and returning the requested data.
 */
interface IClassification {
    fun dataStats(): DataStatsResponse
    fun trainerInfo(): String
    fun trainAndEvaluate(): EvaluationResponse
    fun confusionMatrix(): ConfusionMatrixResponse
    fun serializedModel(): ModelWrapper
}

/**
 * Enclave implementation of the [Classification] tutorial.
 * This class is responsible for executing the [Classification] logic
 * requested by the client.
 * @param id unique instance id for the [Classification] tutorial.
 * @param irisDataPath irises dataset file path.
 * @param trainProportion the proportion of the data to select for training.
 * This should be a number between 0 and 1. For example, a value of 0.7 means
 * that 70% of the data should be selected for the training set.
 * @param seed The seed for the RNG.
 */
class Classification(id: Int, irisDataPath: String, trainProportion: Double, seed: Long) : IClassification, TribuoObject(id) {
    companion object {
        fun evaluationResponse(testingDataset: MutableDataset<Label>, evaluation: LabelEvaluation): EvaluationResponse {
            val classesEvaluationResults = mutableMapOf<String, ClassEvaluationResult>()
            testingDataset.outputInfo.domain.forEach { label ->
                classesEvaluationResults[label.label] = ClassEvaluationResult(
                        evaluation.confusionMatrix.support(label), evaluation.tp(label),
                        evaluation.fn(label), evaluation.fp(label),
                        evaluation.recall(label), evaluation.precision(label), evaluation.f1(label)
                )
            }
            return EvaluationResponse(classesEvaluationResults, evaluation.accuracy(),
                    evaluation.microAveragedRecall(), evaluation.microAveragedPrecision(), evaluation.microAveragedF1(),
                    evaluation.macroAveragedRecall(), evaluation.macroAveragedPrecision(), evaluation.macroAveragedF1(),
                    evaluation.balancedErrorRate(), evaluation.toString()
            )
        }

        fun confusionMatrixResponse(evaluation: LabelEvaluation): ConfusionMatrixResponse {
            val labels = evaluation.confusionMatrix.domain.map { it.b.toString() }
            val matrix = Array(labels.size) { DoubleArray(labels.size) }
            for (column in labels.indices) {
                for (row in labels.indices) {
                    matrix[row][column] = evaluation.confusion(Label(labels[column]), Label(labels[row]))
                }
            }
            return ConfusionMatrixResponse(labels, matrix, evaluation.confusionMatrix.toString())
        }
    }

    private val trainer: Trainer<Label>
    private val trainingDataset: MutableDataset<Label>
    private val testingDataset: MutableDataset<Label>
    private lateinit var irisModel: Model<Label>
    private lateinit var evaluation: LabelEvaluation

    init {
        val labelFactory = LabelFactory()
        val csvLoader = CSVLoader(labelFactory)
        val irisHeaders = arrayOf("sepalLength", "sepalWidth", "petalLength", "petalWidth", "species")
        val irisesSource = csvLoader.loadDataSource(Paths.get(irisDataPath).toUri().toURL(), "species", irisHeaders)
        val irisSplitter = TrainTestSplitter(irisesSource, trainProportion, seed)
        trainer = LogisticRegressionTrainer()

        trainingDataset = MutableDataset(irisSplitter.train)
        testingDataset = MutableDataset(irisSplitter.test)
    }

    /**
     * @return the training and testing dataset statistics
     */
    override fun dataStats(): DataStatsResponse {
        return DataStatsResponse(trainingDataset, testingDataset)
    }

    /**
     * @return the trainer description of its parameters.
     */
    override fun trainerInfo(): String {
        return trainer.toString()
    }

    /**
     * Train and evaluate the model.
     * @return The evaluation statistics.
     */
    override fun trainAndEvaluate(): EvaluationResponse {
        // Training the model
        irisModel = trainer.train(trainingDataset)

        // Evaluating the model
        val evaluator = LabelEvaluator()
        evaluation = evaluator.evaluate(irisModel, testingDataset)
        return evaluationResponse(testingDataset, evaluation)
    }

    /**
     * @return The confusion matrix.
     */
    override fun confusionMatrix(): ConfusionMatrixResponse {
        return confusionMatrixResponse(evaluation)
    }

    /**
     * @return The serialized model.
     */
    override fun serializedModel(): ModelWrapper {
        return ModelWrapper(irisModel)
    }
}

/**
 * Request for the initialization of a [Classification] instance in the enclave.
 * @param irisDataPath irises dataset file path
 * @param trainProportion the proportion of the data to select for training.
 * This should be a number between 0 and 1. For example, a value of 0.7 means
 * that 70% of the data should be selected for the training set.
 * @param seed The seed for the RNG.
 */
@Serializable
data class ClassificationInitializeImpl(val irisDataPath: String, val trainProportion: Double, val seed: Long) : TribuoTask() {
    /**
     * Initializes a [Classification] instance in the enclave.
     * @return The unique id of the [Classification] instance.
     */
    override fun execute(): ByteArray {
        return encode {
            Classification(id.getAndIncrement(), irisDataPath, trainProportion, seed)
        }
    }
}

/**
 * Request for obtaining the training and testing dataset statistics.
 * @param id The [Classification] instance id on which to execute the request.
 */
@Serializable
class DataStats(private val id: Int) : TribuoTask() {
    /**
     * Obtains the training and testing dataset statistics from the
     * requested instance.
     * @return The serialized statistics.
     */
    override fun execute(): ByteArray {
        return encode(id) { classification: Classification ->
            classification.dataStats()
        }
    }
}

@Serializable(with = DataStatsResponseSerializer::class)
data class DataStatsResponse(val trainingDataset: Dataset<*>, val testingDataset: Dataset<*>) {
    override fun toString(): String {
        return String.format("Training data size = %d, number of features = %d, number of classes = %d%n" +
                "Testing data size = %d, number of features = %d, number of classes = %d",
                trainingDataset.size(), trainingDataset.featureMap.size(), trainingDataset.outputInfo.size(),
                testingDataset.size(), testingDataset.featureMap.size(), testingDataset.outputInfo.size())
    }
}

/**
 * Request for obtaining the trainer description of its parameters.
 * @param id The [Classification] instance id on which to execute the request.
 */
@Serializable
class TrainerInfo(private val id: Int) : TribuoTask() {
    /**
     * Obtains the trainer description from the requested instance.
     * @return The serialized trainer description.
     */
    override fun execute(): ByteArray {
        return encode(id) { classification: Classification ->
            classification.trainerInfo()
        }
    }
}

/**
 * Request for training the irises model and obtaining evaluating statistics.
 * @param id The [Classification] instance id on which to execute the request.
 */
@Serializable
class TrainAndEvaluate(private val id: Int) : TribuoTask() {
    /**
     * Trains the irises model and returns the evaluation statistics.
     * @return The serialized evaluation statistics.
     */
    override fun execute(): ByteArray {
        return encode(id) { classification: Classification ->
            classification.trainAndEvaluate()
        }
    }
}

@Serializable
class ClassEvaluationResult(val n: Double, val tp: Double, val fn: Double, val fp: Double,
                            val recall: Double, val precision: Double, val f1: Double)

@Serializable
class EvaluationResponse(val classesEvaluationResults: Map<String, ClassEvaluationResult>, val accuracy: Double,
                         val microAverageRecall: Double, val microAveragePrecision: Double, val microAverageF1: Double,
                         val macroAverageRecall: Double, val macroAveragePrecision: Double, val macroAverageF1: Double,
                         val balancedErrorRate: Double, val summary: String)

/**
 * Request for the confusion matrix.
 * @param id The [Classification] instance id on which to execute the request.
 */
@Serializable
class ConfusionMatrix(private val id: Int) : TribuoTask() {
    /**
     * Obtains the confusion matrix.
     * @return The serialized confusion matrix.
     */
    override fun execute(): ByteArray {
        return encode(id) { classification: Classification ->
            classification.confusionMatrix()
        }
    }
}

@Serializable
class ConfusionMatrixResponse(val labels: List<String>, val matrix: Array<DoubleArray>, val summary: String)

/**
 * Request for serializing the irises model.
 * @param id The [Classification] instance id on which to execute the request.
 */
@Serializable
class SerializedModel(private val id: Int) : TribuoTask() {
    /**
     * Obtains the serialized irises model.
     * @return The serialized model.
     */
    override fun execute(): ByteArray {
        return encode(id) { classification: Classification ->
            classification.serializedModel()
        }
    }
}

@Serializable(with = ModelWrapperSerializer::class)
data class ModelWrapper(@Contextual val value: Model<*>)

/**
 * Request for loading a model.
 * @param model The serialized model to load.
 */
@Serializable
class LoadModel(private val model: ModelWrapper) : TribuoTask() {
    /**
     * Loads the model and checks it's validity. Returns a statement
     * with the recognized or otherwise unrecognized model.
     * @return The serialized recognition statement.
     */
    override fun execute(): ByteArray {
        val loadedModel = model.value
        val result = if (loadedModel.validate(Label::class.java)) {
            "It's a Model<Label>!"
        } else {
            "It's some other kind of Model."
        }
        return Json.encodeToString(result).toByteArray()
    }
}