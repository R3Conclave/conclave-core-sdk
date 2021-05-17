package com.r3.conclave.integrationtests.tribuo.client

import com.r3.conclave.integrationtests.tribuo.common.*
import java.io.Closeable

/**
 * This class is responsible for abstracting the [Regression] tutorial
 * communication between the client and the enclave.
 */
class Regression(private val client: Client) : IRegression, Closeable {
    companion object {
        const val epochs = 10
        const val seed = 1L
        const val maxDepth = 6
    }

    /**
     * Sends the wine quality data set so that when the [Regression] tutorial
     * is initialized the data is already available.
     */
    private var wineDataPath: String = client.sendResource("winequality-red.csv")

    /**
     * Request the initialization of the [Regression] tutorial.
     * The enclave returns the unique id of the tutorial to use when
     * executing the remote procedure calls.
     */
    private val id: Int = client.sendAndReceive(InitializeRegression(wineDataPath, 0.7, 0L))

    /**
     * Request training of SGD model.
     * @param epochs The number of epochs (complete passes through the training data).
     * @param seed A seed for the random number generator, used to shuffle the examples before each epoch.
     * @return The evaluation data.
     */
    override fun trainSGD(epochs: Int, seed: Long): RegressionEvaluationResponse {
        return client.sendAndReceive(TrainSGD(id, epochs, seed))
    }

    /**
     * Request training of AdaGrad model.
     * @return The evaluation data.
     */
    override fun trainAdaGrad(epochs: Int, seed: Long): RegressionEvaluationResponse {
        return client.sendAndReceive(TrainAdaGrad(id, epochs, seed))
    }

    /**
     * Request training of CART model.
     * @return The evaluation data.
     */
    override fun trainCART(maxDepth: Int): RegressionEvaluationResponse {
        return client.sendAndReceive(TrainCART(id, maxDepth))
    }

    /**
     * Delete the wine quality dataset from the enclave.
     */
    override fun close() {
        client.deleteFile(wineDataPath)
    }
}
