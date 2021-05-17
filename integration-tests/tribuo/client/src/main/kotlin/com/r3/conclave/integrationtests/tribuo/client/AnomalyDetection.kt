package com.r3.conclave.integrationtests.tribuo.client

import com.r3.conclave.integrationtests.tribuo.common.AnomalyDetection
import com.r3.conclave.integrationtests.tribuo.common.AnomalyEvaluationResult
import com.r3.conclave.integrationtests.tribuo.common.IAnomalyDetection

/**
 * This class is responsible for abstracting the [AnomalyDetection] tutorial
 * communication between the client and the enclave.
 */
class AnomalyDetection(private val client: Client) : IAnomalyDetection {
    /**
     * Request the initialization of the [AnomalyDetection] tutorial.
     * The enclave returns the unique id of the tutorial to use when
     * executing the remote procedure calls.
     */
    private val id: Int = client.sendAndReceive(AnomalyDetection.InitializeAnomalyDetection(2000L, 0.2))

    /**
     * Request training and evaluation of SVM.
     * @return test evaluation results.
     */
    override fun trainAndEvaluate(): AnomalyEvaluationResult {
        return client.sendAndReceive(AnomalyDetection.TrainAndEvaluate(id))
    }

    /**
     * Request confusion matrix.
     * @return confusion matrix.
     */
    override fun confusionMatrix(): String {
        return client.sendAndReceive(AnomalyDetection.ConfusionMatrix(id))
    }
}