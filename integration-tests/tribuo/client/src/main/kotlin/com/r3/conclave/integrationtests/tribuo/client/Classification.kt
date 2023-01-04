package com.r3.conclave.integrationtests.tribuo.client

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.google.gson.GsonBuilder
import com.oracle.labs.mlrg.olcut.config.json.JsonProvenanceModule
import com.oracle.labs.mlrg.olcut.provenance.ProvenanceUtil
import com.r3.conclave.integrationtests.tribuo.common.*
import java.io.Closeable

/**
 * This class is responsible for abstracting the [Classification] tutorial
 * communication between the client and the enclave.
 */
class Classification(private val client: Client) : IClassification, Closeable {
    companion object {
        /**
         * Irises dataset file name
         */
        const val IRIS_DATA_FILE_NAME = "bezdekIris.data"
    }

    /**
     * Send the irises dataset to the enclave so that when [Classification] tutorial
     * is initialized the data is already available.
     */
    val irisDataPath: String = client.sendResource(IRIS_DATA_FILE_NAME)

    /**
     * Sends a message to the enclave requesting the initialization of the
     * [Classification] tutorial.
     * The enclave returns the unique id of the tutorial to use when
     * executing the remote procedure calls.
     */
    private val id: Int = client.sendAndReceive(ClassificationInitializeImpl(irisDataPath, 0.7, 1L))

    lateinit var model: ModelWrapper

    /**
     * Request the training and testing dataset statistics.
     */
    override fun dataStats(): DataStatsResponse {
        return client.sendAndReceive(DataStats(id))
    }

    /**
     * Requests the trainer description of its parameters.
     */
    override fun trainerInfo(): String {
        return client.sendAndReceive(TrainerInfo(id))
    }

    /**
     * Request model training and evaluation statistics.
     */
    override fun trainAndEvaluate(): EvaluationResponse {
        return client.sendAndReceive(TrainAndEvaluate(id))
    }

    /**
     * Request the confusion matrix.
     */
    override fun confusionMatrix(): ConfusionMatrixResponse {
        return client.sendAndReceive(ConfusionMatrix(id))
    }

    /**
     * Save the model by requesting it to be serialized out of the enclave.
     */
    override fun serializedModel(): ModelWrapper {
        model = client.sendAndReceive(SerializedModel(id))
        return model
    }

    /**
     * Load a model into the enclave, for example, a trained one
     * which has been serialized out via [Classification.serializedModel].
     * @param model The model to load into the enclave
     */
    fun loadModel(model: ModelWrapper): String {
        return client.sendAndReceive(LoadModel(model))
    }

    /**
     * @return model metadata.
     */
    fun modelMetadata(): String {
        return model.value.featureIDMap.joinToString(System.lineSeparator())
    }

    /**
     * @return data provenance for the irises model.
     */
    fun modelProvenance(): String {
        return ProvenanceUtil.formattedProvenanceString(model.value.provenance.datasetProvenance.sourceProvenance) + System.lineSeparator() +
                ProvenanceUtil.formattedProvenanceString(model.value.provenance.trainerProvenance)
    }

    /**
     * @return data provenance in JSON format.
     */
    fun jsonProvenance(): String {
        // This need to be fixed before merging
        // JsonProvenanceModule uses Jackson internally so we need to look for an alternative
        // This is an integration test so if we end up using Jackson it won't be that mad
//        var gson = GsonBuilder().registerTypeAdapter(JsonProvenanceModule()).create()
//        gson = gson.enable(SerializationFeature.INDENT_OUTPUT)
//        return gson.writeValueAsString(ProvenanceUtil.marshalProvenance(model.value.provenance))
        return ""
    }

    /**
     * Delete the irises dataset from the enclave.
     */
    override fun close() {
        client.deleteFile(irisDataPath)
    }
}