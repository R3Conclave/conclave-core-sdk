package com.r3.conclave.integrationtests.tribuo.common

import com.oracle.labs.mlrg.olcut.config.ConfigurationManager
import com.oracle.labs.mlrg.olcut.config.json.JsonConfigFactory
import com.oracle.labs.mlrg.olcut.provenance.ProvenanceUtil
import com.oracle.labs.mlrg.olcut.provenance.primitives.BooleanProvenance
import kotlinx.serialization.Serializable
import org.tribuo.DataSource
import org.tribuo.Model
import org.tribuo.MutableDataset
import org.tribuo.Trainer
import org.tribuo.classification.Label
import org.tribuo.classification.evaluation.LabelEvaluation
import org.tribuo.classification.evaluation.LabelEvaluator
import org.tribuo.transform.TransformTrainer
import org.tribuo.transform.TransformationMap
import org.tribuo.transform.transformations.LinearScalingTransformation
import java.io.File
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.io.path.readBytes

/**
 * Common interface between client and enclave.
 * The client's implementation is responsible for sending
 * requests to the enclave.
 * The enclave's implementation is responsible for processing
 * those requests and returning the requested data.
 */
interface IConfiguration {
    fun dataStats(): String
    fun initializeLogisticTrainer(): String
    fun mnistLogisticConfig(): ByteArray
    fun lrEvaluator(): EvaluationResponse
    fun lrEvaluatorConfusionMatrix(): ConfusionMatrixResponse
    fun newEvaluator(): EvaluationResponse
    fun newEvaluatorConfusionMatrix(): ConfusionMatrixResponse
    fun newEvaluatorProvenance(): String
    fun transformedEvaluator(): EvaluationResponse
    fun transformedEvaluatorConfusionMatrix(): ConfusionMatrixResponse
    fun mnistTransformedLogisticConfig(): ByteArray
}

/**
 * Enclave implementation of the [Configuration] tutorial.
 * This class is responsible for executing the [Configuration] logic
 * requested by the client.
 * @param id unique instance id for the [Configuration] tutorial.
 * @param configurationFile configuration file path.
 */
class Configuration(id: Int, configurationFile: String) : IConfiguration, TribuoObject(id) {
    companion object {
        const val TRAIN_LABELS_IDX_1_FILE_NAME = "train-labels-idx1-ubyte.gz"
        const val TRAIN_IMAGES_IDX_3_FILE_NAME = "train-images-idx3-ubyte.gz"
        const val T10K_LABELS_IDX_1_FILE_NAME = "t10k-labels-idx1-ubyte.gz"
        const val T10K_IMAGES_IDX_3_FILE_NAME = "t10k-images-idx3-ubyte.gz"
        const val MNIST_LOGISTIC_CONFIG_FILE_NAME = "mnist-logistic-config.json"
        const val MNIST_TRANSFORMED_LOGISTIC_CONFIG_FILE_NAME = "mnist-transformed-logistic-config.json"
    }

    private val config: Path = Paths.get(configurationFile)
    private var cm: ConfigurationManager
    private var trainData: MutableDataset<Label>
    private var testData: MutableDataset<Label>
    private var evaluator: LabelEvaluator
    private lateinit var logistic: Trainer<Label>
    private lateinit var lrModel: Model<Label>
    private lateinit var newCM: ConfigurationManager
    private lateinit var lrEvaluator: LabelEvaluation
    private lateinit var newTrainer: Trainer<Label>
    private lateinit var newDataset: MutableDataset<Label>
    private lateinit var newModel: Model<Label>
    private lateinit var newEvaluator: LabelEvaluation
    private lateinit var transformedEvaluator: LabelEvaluation
    private lateinit var transformedModel: Model<Label>

    init {
        ConfigurationManager.addFileFormatFactory(JsonConfigFactory())

        cm = ConfigurationManager(config.toUri().toURL())
        @Suppress("UNCHECKED_CAST") val mnistTrain = cm.lookup("mnist-train") as DataSource<Label>
        @Suppress("UNCHECKED_CAST") val mnistTest = cm.lookup("mnist-test") as DataSource<Label>
        trainData = MutableDataset(mnistTrain)
        testData = MutableDataset(mnistTest)
        evaluator = LabelEvaluator()
    }

    /**
     * @return Training and test dataset statistics.
     */
    override fun dataStats(): String {
        val trainingDataStats = String.format("Training data size = %d, number of features = %d, number of classes = %d%n",
                trainData.size(), trainData.featureMap.size(), trainData.outputInfo.size())
        val testDataStats = String.format("Testing data size = %d, number of features = %d, number of classes = %d%n",
                testData.size(), testData.featureMap.size(), testData.outputInfo.size())
        return "$trainingDataStats$testDataStats"
    }

    /**
     * Initializes the logistic trainer.
     * @return Trainer's statistics.
     */
    override fun initializeLogisticTrainer(): String {
        @Suppress("UNCHECKED_CAST")
        logistic = cm.lookup("logistic") as Trainer<Label>
        return logistic.toString()
    }

    /**
     * Train the logistic regression model.
     * @return MNIST logistic configuration in JSON format.
     */
    override fun mnistLogisticConfig(): ByteArray {
        lrModel = logistic.train(trainData)
        val provenance = lrModel.provenance
        val provConfig = ProvenanceUtil.extractConfiguration(provenance)
        println("provConfig.size(): " + provConfig.size)

        newCM = ConfigurationManager()
        newCM.addConfiguration(provConfig)
        val mnistLogisticConfig = config.parent.resolve(MNIST_LOGISTIC_CONFIG_FILE_NAME).toAbsolutePath().toString()
        newCM.save(File(mnistLogisticConfig), true)
        return Paths.get(mnistLogisticConfig).readBytes()
    }

    /**
     * SGD training and evaluation of logistic model.
     * @return test evaluation results.
     */
    override fun lrEvaluator(): EvaluationResponse {
        @Suppress("UNCHECKED_CAST")
        newTrainer = newCM.lookup("linearsgdtrainer-0") as Trainer<Label>
        @Suppress("UNCHECKED_CAST")
        val newSource = newCM.lookup("idxdatasource-1") as DataSource<Label>
        newDataset = MutableDataset(newSource)
        newModel = newTrainer.train(newDataset, mapOf("reconfigured-model" to BooleanProvenance("reconfigured-model", true)))
        println(lrModel == newModel)
        lrEvaluator = evaluator.evaluate(lrModel, testData)
        return Classification.evaluationResponse(testData, lrEvaluator)
    }

    /**
     * Logistic evaluator confusion matrix.
     * @return confusion matrix.
     */
    override fun lrEvaluatorConfusionMatrix(): ConfusionMatrixResponse {
        return Classification.confusionMatrixResponse(lrEvaluator)
    }

    /**
     * Evaluate SGD model.
     * @return test evaluation results.
     */
    override fun newEvaluator(): EvaluationResponse {
        newEvaluator = evaluator.evaluate(newModel, testData)
        return Classification.evaluationResponse(testData, newEvaluator)
    }

    /**
     * SGD evaluator confusion matrix.
     * @return confusion matrix.
     */
    override fun newEvaluatorConfusionMatrix(): ConfusionMatrixResponse {
        return Classification.confusionMatrixResponse(newEvaluator)
    }

    /**
     * SGD evaluator provenance data.
     * @return provenance data.
     */
    override fun newEvaluatorProvenance(): String {
        return ProvenanceUtil.formattedProvenanceString(newEvaluator.provenance)
    }

    /**
     * LinearScalingTransformation training and evaluation.
     * @return test evaluation results.
     */
    override fun transformedEvaluator(): EvaluationResponse {
        val transformations = TransformationMap(listOf(LinearScalingTransformation(0.0, 1.0)))
        val transformed = TransformTrainer(newTrainer, transformations)
        transformedModel = transformed.train(newDataset)

        transformedEvaluator = evaluator.evaluate(transformedModel, testData)
        return Classification.evaluationResponse(testData, transformedEvaluator)
    }

    /**
     * Transformed evaluator confusion matrix.
     * @return confusion matrix.
     */
    override fun transformedEvaluatorConfusionMatrix(): ConfusionMatrixResponse {
        return Classification.confusionMatrixResponse(transformedEvaluator)
    }

    /**
     * Configuration of transformation and original trainers.
     * @return configuration.
     */
    override fun mnistTransformedLogisticConfig(): ByteArray {
        val transformedProvConfig = ProvenanceUtil.extractConfiguration(transformedModel.provenance)
        val transformedOutputFile = config.parent.resolve(MNIST_TRANSFORMED_LOGISTIC_CONFIG_FILE_NAME).toAbsolutePath().toString()
        newCM = ConfigurationManager()
        newCM.addConfiguration(transformedProvConfig)
        newCM.save(File(transformedOutputFile), true)
        return Paths.get(transformedOutputFile).readBytes()
    }

    /**
     * Request for the initialization of the [Configuration] tutorial.
     * @param configurationFile configuration file path.
     */
    @Serializable
    class InitializeConfiguration(private val configurationFile: String) : TribuoTask() {
        /**
         * Initializes a [Configuration] instance in the enclave.
         * @return The unique id of the [Configuration] instance.
         */
        override fun execute(): ByteArray {
            return encode {
                Configuration(id.getAndIncrement(), configurationFile)
            }
        }
    }

    /**
     * Request for the training and test dataset statistics.
     * @param id The unique id of the [Configuration] instance.
     */
    @Serializable
    class DataStats(private val id: Int) : TribuoTask() {
        /**
         * Training and test dataset statistics.
         * @return serialized training and test dataset statistics.
         */
        override fun execute(): ByteArray {
            return encode(id) { configuration: Configuration ->
                configuration.dataStats()
            }
        }
    }

    /**
     * Request for initializing the logistic trainer.
     * @param id The unique id of the [Configuration] instance.
     */
    @Serializable
    class InitializeLogisticTrainer(private val id: Int) : TribuoTask() {
        override fun execute(): ByteArray {
            return encode(id) { configuration: Configuration ->
                configuration.initializeLogisticTrainer()
            }
        }
    }

    /**
     * Request for the logistic model training and configuration.
     * @param id The unique id of the [Configuration] instance.
     */
    @Serializable
    class MnistLogisticConfig(private val id: Int) : TribuoTask() {
        /**
         * Train the logistic regression model.
         * @return serialized configuration.
         */
        override fun execute(): ByteArray {
            return encode(id) { configuration: Configuration ->
                configuration.mnistLogisticConfig()
            }
        }
    }

    /**
     * Request for the SGD training and evaluation of the logistic model.
     * @param id The unique id of the [Configuration] instance.
     */
    @Serializable
    class LrEvaluator(private val id: Int) : TribuoTask() {
        /**
         * SGD training and evaluation of logistic model.
         * @return serialized test evaluation results.
         */
        override fun execute(): ByteArray {
            return encode(id) { configuration: Configuration ->
                configuration.lrEvaluator()
            }
        }
    }

    /**
     * Request for the logistic evaluator confusion matrix.
     * @param id The unique id of the [Configuration] instance.
     */
    @Serializable
    class LrEvaluatorConfusionMatrix(private val id: Int) : TribuoTask() {
        override fun execute(): ByteArray {
            /**
             * Logistic evaluator confusion matrix.
             * @return serialized confusion matrix.
             */
            return encode(id) { configuration: Configuration ->
                configuration.lrEvaluatorConfusionMatrix()
            }
        }
    }

    /**
     * Request for the SGD model evaluation.
     * @param id The unique id of the [Configuration] instance.
     */
    @Serializable
    class NewEvaluator(private val id: Int) : TribuoTask() {
        /**
         * Evaluate SGD model.
         * @return serialized test evaluation results.
         */
        override fun execute(): ByteArray {
            return encode(id) { configuration: Configuration ->
                configuration.newEvaluator()
            }
        }
    }

    /**
     * Request for the SGD evaluator confusion matrix.
     * @param id The unique id of the [Configuration] instance.
     */
    @Serializable
    class NewEvaluatorConfusionMatrix(private val id: Int) : TribuoTask() {
        /**
         * SGD evaluator confusion matrix.
         * @return serialized confusion matrix.
         */
        override fun execute(): ByteArray {
            return encode(id) { configuration: Configuration ->
                configuration.newEvaluatorConfusionMatrix()
            }
        }
    }

    /**
     * Request SGD evaluator provenance data.
     * @param id The unique id of the [Configuration] instance.
     */
    @Serializable
    class NewEvaluatorProvenance(private val id: Int) : TribuoTask() {
        /**
         * SGD evaluator provenance data.
         * @return serialized provenance data.
         */
        override fun execute(): ByteArray {
            return encode(id) { configuration: Configuration ->
                configuration.newEvaluatorProvenance()
            }
        }
    }

    /**
     * Request for the LinearScalingTransformation training and evaluation results.
     * @param id The unique id of the [Configuration] instance.
     */
    @Serializable
    class TransformedEvaluator(private val id: Int) : TribuoTask() {
        /**
         * LinearScalingTransformation training and evaluation.
         * @return serialized test evaluation results.
         */
        override fun execute(): ByteArray {
            return encode(id) { configuration: Configuration ->
                configuration.transformedEvaluator()
            }
        }
    }

    /**
     * Request for the transformed evaluator confusion matrix.
     * @param id The unique id of the [Configuration] instance.
     */
    @Serializable
    class TransformedEvaluatorConfusionMatrix(private val id: Int) : TribuoTask() {
        /**
         * Transformed evaluator confusion matrix.
         * @return serialized confusion matrix.
         */
        override fun execute(): ByteArray {
            return encode(id) { configuration: Configuration ->
                configuration.transformedEvaluatorConfusionMatrix()
            }
        }
    }

    /**
     * Request for the configuration of the transformation and original trainers.
     * @param id The unique id of the [Configuration] instance.
     */
    @Serializable
    class MnistTransformedLogisticConfig(private val id: Int) : TribuoTask() {
        /**
         * Configuration of transformation and original trainers.
         * @return serialized configuration.
         */
        override fun execute(): ByteArray {
            return encode(id) { configuration: Configuration ->
                configuration.mnistTransformedLogisticConfig()
            }
        }
    }
}