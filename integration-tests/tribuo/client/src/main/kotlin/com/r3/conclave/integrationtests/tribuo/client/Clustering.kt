package com.r3.conclave.integrationtests.tribuo.client

import com.r3.conclave.integrationtests.tribuo.common.*
import org.tribuo.clustering.kmeans.KMeansTrainer

/**
 * This class is responsible for abstracting the [Clustering] tutorial
 * communication between the client and the enclave.
 */
class Clustering(private val client: Client) : IClustering {
    companion object {
        const val centroids = 5
        const val iterations = 10
        val distanceType = KMeansTrainer.Distance.EUCLIDEAN
        const val numThreads = 1
        const val seed = 1L
    }
    /**
     * Sends a message to the enclave requesting the initialization of the
     * [Clustering] tutorial.
     * The enclave returns the unique id of the tutorial to use when
     * executing the remote procedure calls.
     */
    private val id: Int = client.sendAndReceive(ClusteringInitializeImpl(500L, 1L))

    /**
     * Request model training and evaluation results.
     * @param centroids The number of centroids to use.
     * @param iterations The maximum number of iterations.
     * @param distanceType The distance function.
     * @param numThreads The number of threads.
     * @param seed The random seed.
     * @return training evaluation results.
     */
    override fun train(centroids: Int, iterations: Int, distanceType: KMeansTrainer.Distance, numThreads: Int, seed: Long): ClusteringEvaluationResponse {
        return client.sendAndReceive(Train(id, centroids, iterations, distanceType, numThreads, seed))
    }

    /**
     * Request centroids data.
     */
    override fun centroids(): CentroidsResponse {
        return client.sendAndReceive(Centroids(id))
    }

    /**
     * Request test data evaluation results.
     */
    override fun evaluate(): ClusteringEvaluationResponse {
        return client.sendAndReceive(Evaluate(id))
    }
}
