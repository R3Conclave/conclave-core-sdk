package com.r3.conclave.integrationtests.tribuo.common

import com.r3.conclave.integrationtests.tribuo.common.TribuoObject.Companion.encode
import com.r3.conclave.integrationtests.tribuo.common.TribuoObject.Companion.id
import kotlinx.serialization.Serializable
import org.tribuo.Dataset
import org.tribuo.clustering.ClusterID
import org.tribuo.clustering.evaluation.ClusteringEvaluator
import org.tribuo.clustering.example.ClusteringDataGenerator
import org.tribuo.clustering.kmeans.KMeansModel
import org.tribuo.clustering.kmeans.KMeansTrainer
import org.tribuo.math.la.DenseVector

/**
 * Common interface between client and enclave.
 * The client's implementation is responsible for sending
 * requests to the enclave.
 * The enclave's implementation is responsible for processing
 * those requests and returning the requested data.
 */
interface IClustering {
    fun train(centroids: Int, iterations: Int, distanceType: KMeansTrainer.Distance,
              numThreads: Int, seed: Long): ClusteringEvaluationResponse
    fun centroids(): CentroidsResponse
    fun evaluate(): ClusteringEvaluationResponse
}

/**
 * Enclave implementation of the [Clustering] tutorial.
 * This class is responsible for executing the [Clustering] logic
 * requested by the client.
 * @param id unique instance id for the [Clustering] tutorial.
 * @param size The number of points to sample for the dataset.
 * @param seed The RNG seed.
 */
class Clustering(id: Int, size: Long, seed: Long): IClustering, TribuoObject(id) {
    private val eval: ClusteringEvaluator = ClusteringEvaluator()
    private val data: Dataset<ClusterID> = ClusteringDataGenerator.gaussianClusters(size, seed)
    private var test: Dataset<ClusterID> = ClusteringDataGenerator.gaussianClusters(size, seed + 1)
    private lateinit var trainer: KMeansTrainer
    private lateinit var model: KMeansModel

    /**
     * Model training.
     * @param centroids The number of centroids to use.
     * @param iterations The maximum number of iterations.
     * @param distanceType The distance function.
     * @param numThreads The number of threads.
     * @param seed The random seed.
     * @return Training evaluation results.
     */
    override fun train(centroids: Int, iterations: Int, distanceType: KMeansTrainer.Distance, numThreads: Int, seed: Long): ClusteringEvaluationResponse {
        trainer = KMeansTrainer(centroids, iterations, distanceType, numThreads, seed)
        model = trainer.train(data)
        val trainEvaluation = eval.evaluate(model, data)
        return ClusteringEvaluationResponse(trainEvaluation.normalizedMI(), trainEvaluation.adjustedMI())
    }

    /**
     * @return centroids data.
     */
    override fun centroids(): CentroidsResponse {
        return CentroidsResponse(model.centroidVectors)
    }

    /**
     * Evaluate test data.
     * @return Test evaluation data.
     */
    override fun evaluate(): ClusteringEvaluationResponse {
        val evaluation = eval.evaluate(model, test)
        return ClusteringEvaluationResponse(evaluation.normalizedMI(), evaluation.adjustedMI())
    }
}

/**
 * Request for the initialization of a [Clustering] instance in the enclave.
 * @param size The number of points to sample for the dataset.
 * @param seed The RNG seed.
 */
@Serializable
class ClusteringInitializeImpl(private val size: Long, private val seed: Long) : TribuoTask() {
    /**
     * Initializes a [Clustering] instance in the enclave.
     * @return The unique id of the [Clustering] instance.
     */
    override fun execute(): ByteArray {
        return encode {
            Clustering(id.getAndIncrement(), size, seed)
        }
    }
}

/**
 * Request for training the model.
 * @param id The [Clustering] instance id on which to execute the request.
 * @param centroids The number of centroids to use.
 * @param iterations The maximum number of iterations.
 * @param distanceType The distance function.
 * @param numThreads The number of threads.
 * @param seed The random seed.
 */
@Serializable
class Train(private val id: Int, private val centroids: Int,
            private val iterations: Int, private val distanceType: KMeansTrainer.Distance,
            private val numThreads: Int, private val seed: Long) : TribuoTask() {
    /**
     * Trains the model and returns the evaluation results.
     * @return The serialized training evaluation results.
     */
    override fun execute(): ByteArray {
        return encode(id) { clustering: Clustering ->
            clustering.train(centroids, iterations, distanceType, numThreads, seed)
        }
    }
}

@Serializable
data class ClusteringEvaluationResponse(val normalizedMI: Double, val adjustedMI: Double) {
    override fun toString(): String {
        return "Normalized MI = $normalizedMI${System.lineSeparator()}" +
                "Adjusted MI = $adjustedMI"
    }
}

/**
 * Request for centroids data.
 * @param id The [Clustering] instance id on which to execute the request.
 */
@Serializable
class Centroids(private val id: Int) : TribuoTask() {
    /**
     * Obtains the centroids data.
     * @return Serialized centroids data.
     */
    override fun execute(): ByteArray {
        return encode(id) { clustering: Clustering ->
            clustering.centroids()
        }
    }
}

@Serializable(with = CentroidsResponseSerializer::class)
class CentroidsResponse(val centroids: Array<DenseVector>) {
    override fun toString(): String {
        return centroids.contentToString()
    }
}

/**
 * Request for evaluating the test data.
 * @param id The [Clustering] instance id on which to execute the request.
 */
@Serializable
class Evaluate(private val id: Int) : TribuoTask() {
    /**
     * Evaluates the test data.
     * @return The serialized test data results.
     */
    override fun execute(): ByteArray {
        return encode(id) { clustering: Clustering ->
            clustering.evaluate()
        }
    }
}
