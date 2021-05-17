package com.r3.conclave.integrationtests.tribuo.client

import com.r3.conclave.integrationtests.tribuo.common.*
import com.r3.conclave.integrationtests.tribuo.common.Configuration
import org.tribuo.classification.evaluation.LabelEvaluation
import java.io.Closeable
import java.io.File

/**
 * This class is responsible for abstracting the [Configuration] tutorial
 * communication between the client and the enclave.
 */
class Configuration(private val client: Client) : IConfiguration, Closeable {
    /**
     * Send configuration file to the enclave. The enclave will respond with
     * the file path in the enclave.
     * The file contents are adjusted when running in mock mode as per
     * [MockFileManager.sendFile] description.
     */
    private val configurationFile: String = client.sendResource("example-config.json") {
        String(it)
                .replace(": \"/".toRegex(), ": \"${client.enclaveConfiguration.fileManager.dataDir}${File.separator}")
                .toByteArray()
    }

    /**
     * Send images and labels resources to the enclave.
     */
    private val t10kImagesResourceFile: String = client.sendResource("t10k-images-idx3-ubyte.gz")
    private val t10kLabelsResourceFile: String = client.sendResource("t10k-labels-idx1-ubyte.gz")
    private val trainImagesResourceFile: String = client.sendResource("train-images-idx3-ubyte.gz")
    private val trainLabelsResourceFile: String = client.sendResource("train-labels-idx1-ubyte.gz")

    /**
     * Request the initialization of the [Configuration] tutorial.
     * The enclave returns the unique id of the tutorial to use when
     * executing the remote procedure calls.
     */
    private val id: Int = client.sendAndReceive(Configuration.InitializeConfiguration(configurationFile))

    /**
     * Request training and test dataset statistics.
     * @return Training and test dataset statistics.
     */
    override fun dataStats(): String {
        return client.sendAndReceive(Configuration.DataStats(id))
    }

    /**
     * Request the initialization of the logistic trainer.
     * @return Trainer's statistics.
     */
    override fun initializeLogisticTrainer(): String {
        return client.sendAndReceive(Configuration.InitializeLogisticTrainer(id))
    }

    /**
     * Request logistic model training and obtain MNIST logistic configuration.
     * @return MNIST logistic configuration.
     */
    override fun mnistLogisticConfig(): ByteArray {
        return client.sendAndReceive(Configuration.MnistLogisticConfig(id))
    }

    /**
     * Request SGD training and evaluation of logistic model.
     * @return test evaluation results.
     */
    override fun lrEvaluator(): EvaluationResponse {
        return client.sendAndReceive(Configuration.LrEvaluator(id))
    }

    /**
     * Request logistic evaluator confusion matrix.
     * @return confusion matrix.
     */
    override fun lrEvaluatorConfusionMatrix(): ConfusionMatrixResponse {
        return client.sendAndReceive(Configuration.LrEvaluatorConfusionMatrix(id))
    }

    /**
     * Evaluate SGD model.
     * @return test evaluation results.
     */
    override fun newEvaluator(): EvaluationResponse {
        return client.sendAndReceive(Configuration.NewEvaluator(id))
    }

    /**
     * Request SGD evaluator confusion matrix.
     * @return confusion matrix.
     */
    override fun newEvaluatorConfusionMatrix(): ConfusionMatrixResponse {
        return client.sendAndReceive(Configuration.NewEvaluatorConfusionMatrix(id))
    }

    /**
     * Request SGD evaluator provenance data.
     * @return provenance data.
     */
    override fun newEvaluatorProvenance(): String {
        return client.sendAndReceive(Configuration.NewEvaluatorProvenance(id))
    }

    /**
     * Request LinearScalingTransformation training and evaluation results.
     * @return test evaluation results.
     */
    override fun transformedEvaluator(): EvaluationResponse {
        return client.sendAndReceive(Configuration.TransformedEvaluator(id))
    }

    /**
     * Request transformed evaluator confusion matrix.
     * @return confusion matrix.
     */
    override fun transformedEvaluatorConfusionMatrix(): ConfusionMatrixResponse {
        return client.sendAndReceive(Configuration.TransformedEvaluatorConfusionMatrix(id))
    }

    /**
     * Request configuration of transformation and original trainers.
     * @return configuration.
     */
    override fun mnistTransformedLogisticConfig(): ByteArray {
        return client.sendAndReceive(Configuration.MnistTransformedLogisticConfig(id))
    }

    /**
     * Delete configuration, image and label files from the enclave.
     */
    override fun close() {
        client.deleteFile(configurationFile) {
            String(it)
                    .replace(": \"${client.enclaveConfiguration.fileManager.dataDir}${File.separator}".toRegex(), ": \"/")
                    .toByteArray()
        }
        client.deleteFile(t10kImagesResourceFile)
        client.deleteFile(t10kLabelsResourceFile)
        client.deleteFile(trainImagesResourceFile)
        client.deleteFile(trainLabelsResourceFile)
    }
}