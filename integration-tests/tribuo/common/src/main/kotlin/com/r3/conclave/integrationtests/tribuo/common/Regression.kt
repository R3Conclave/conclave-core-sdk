package com.r3.conclave.integrationtests.tribuo.common

import com.r3.conclave.integrationtests.tribuo.common.TribuoObject.Companion.encode
import com.r3.conclave.integrationtests.tribuo.common.TribuoObject.Companion.id
import kotlinx.serialization.Serializable
import org.tribuo.Dataset
import org.tribuo.Model
import org.tribuo.MutableDataset
import org.tribuo.Trainer
import org.tribuo.data.csv.CSVLoader
import org.tribuo.datasource.ListDataSource
import org.tribuo.evaluation.TrainTestSplitter
import org.tribuo.math.optimisers.AdaGrad
import org.tribuo.math.optimisers.SGD
import org.tribuo.regression.RegressionFactory
import org.tribuo.regression.Regressor
import org.tribuo.regression.evaluation.RegressionEvaluator
import org.tribuo.regression.rtree.CARTRegressionTrainer
import org.tribuo.regression.sgd.linear.LinearSGDTrainer
import org.tribuo.regression.sgd.objectives.SquaredLoss
import java.nio.file.Path
import java.nio.file.Paths

/**
 * Common interface between client and enclave.
 * The client's implementation is responsible for sending
 * requests to the enclave.
 * The enclave's implementation is responsible for processing
 * those requests and returning the requested data.
 */
interface IRegression {
    fun trainSGD(epochs: Int, seed: Long): RegressionEvaluationResponse
    fun trainAdaGrad(epochs: Int, seed: Long): RegressionEvaluationResponse
    fun trainCART(maxDepth: Int): RegressionEvaluationResponse
}

/**
 * Enclave implementation of the [Regression] tutorial.
 * This class is responsible for executing the [Regression] logic
 * requested by the client.
 * @param id unique instance id for the [Regression] tutorial.
 * @param dataPath Wine quality dataset path.
 * @param trainProportion the proportion of the data to select for training.
 * This should be a number between 0 and 1. For example, a value of 0.7 means
 * that 70% of the data should be selected for the training set.
 * @param seed The seed for the RNG.
 */
class Regression(id: Int, dataPath: String, trainProportion: Double, seed: Long) : IRegression, TribuoObject(id) {
    private val trainData: Dataset<Regressor>
    private val evalData: Dataset<Regressor>

    init {
        val regressionFactory = RegressionFactory()
        val csvLoader = CSVLoader(';', regressionFactory)
        val wineDataPath: Path = Paths.get(dataPath)
        val wineSource: ListDataSource<Regressor> = csvLoader.loadDataSource(wineDataPath.toUri().toURL(), "quality")
        val splitter = TrainTestSplitter(wineSource, trainProportion, seed)
        trainData = MutableDataset(splitter.train)
        evalData = MutableDataset(splitter.test)
    }

    /**
     * Train SGD model.
     * @param epochs The number of epochs (complete passes through the training data).
     * @param seed A seed for the random number generator, used to shuffle the examples before each epoch.
     * @return The evaluation data.
     */
    override fun trainSGD(epochs: Int, seed: Long): RegressionEvaluationResponse {
        val lrsgd = LinearSGDTrainer(
                SquaredLoss(),  // loss function
                SGD.getLinearDecaySGD(0.01),  // gradient descent algorithm
                epochs,  // number of training epochs
                trainData.size() / 4,  // logging interval
                1,  // minibatch size
                seed // RNG seed
        )
        val lrsgdModel: Model<Regressor> = regressionTrain("Linear Regression (SGD)", lrsgd, trainData)
        return regressionEvaluate(lrsgdModel, evalData)
    }

    /**
     * Train AdaGrad model.
     * @param epochs The number of epochs (complete passes through the training data).
     * @param seed A seed for the random number generator, used to shuffle the examples before each epoch.
     * @return The evaluation data.
     */
    override fun trainAdaGrad(epochs: Int, seed: Long): RegressionEvaluationResponse {
        val lrada = LinearSGDTrainer(
                SquaredLoss(),
                AdaGrad(0.01),
                epochs,
                trainData.size() / 4,
                1,
                seed
        )
        val lradaModel = regressionTrain("Linear Regression (AdaGrad)", lrada, trainData)
        return regressionEvaluate(lradaModel, evalData)
    }

    /**
     * Train CART model.
     * @param maxDepth The maximum depth of the tree.
     * @return The evaluation data.
     */
    override fun trainCART(maxDepth: Int): RegressionEvaluationResponse {
        val cart = CARTRegressionTrainer(maxDepth)
        val cartModel = regressionTrain("CART", cart, trainData)
        return regressionEvaluate(cartModel, evalData)
    }

    /**
     * Train a model for a given [trainer] and [trainData].
     * @param name Trainer name.
     * @param trainer Trainer instance.
     * @param trainData Training dataset.
     * @return The trained model.
     */
    private fun regressionTrain(name: String, trainer: Trainer<Regressor>, trainData: Dataset<Regressor>): Model<Regressor> {
        // Train the model
        val model = trainer.train(trainData)

        // Evaluate the model on the training data (this is a useful debugging tool)
        val eval = RegressionEvaluator()
        val evaluation = eval.evaluate(model, trainData)
        // We create a dimension here to aid pulling out the appropriate statistics.
        // You can also produce the String directly by calling "evaluation.toString()"
        val dimension = Regressor("DIM-0", Double.NaN)
        System.out.printf("Evaluation (train) %s:%n  RMSE %f%n  MAE %f%n  R^2 %f%n",
                name, evaluation.rmse(dimension), evaluation.mae(dimension), evaluation.r2(dimension))
        return model
    }

    /**
     * Evaluate a [model] for the given [testData].
     * @param model Model to be evaluated.
     * @param testData Test dataset.
     * @return Evaluation data.
     */
    private fun regressionEvaluate(model: Model<Regressor>, testData: Dataset<Regressor>): RegressionEvaluationResponse {
        // Evaluate the model on the test data
        val eval = RegressionEvaluator()
        val evaluation = eval.evaluate(model, testData)
        // We create a dimension here to aid pulling out the appropriate statistics.
        // You can also produce the String directly by calling "evaluation.toString()"
        val dimension = Regressor("DIM-0", Double.NaN)
        return RegressionEvaluationResponse(evaluation.rmse(dimension), evaluation.mae(dimension), evaluation.r2(dimension))
    }
}

/**
 * Request for the initialization of the [Regression] tutorial.
 * @param wineDataPath Wine quality dataset path.
 * @param trainProportion the proportion of the data to select for training.
 * This should be a number between 0 and 1. For example, a value of 0.7 means
 * that 70% of the data should be selected for the training set.
 * @param seed The seed for the RNG.
 */
@Serializable
class InitializeRegression(private val wineDataPath: String, private val trainProportion: Double, private val seed: Long) : TribuoTask() {
    /**
     * Initializes a [Regression] instance in the enclave.
     * @return The unique id of the [Regression] instance.
     */
    override fun execute(): ByteArray {
        return encode {
            Regression(id.getAndIncrement(), wineDataPath, trainProportion, seed)
        }
    }
}

/**
 * Request for training the SGD model.
 * @param id unique instance id for the [Regression] tutorial.
 * @param epochs The number of epochs (complete passes through the training data).
 * @param seed A seed for the random number generator, used to shuffle the examples before each epoch.
 */
@Serializable
class TrainSGD(private val id: Int, private val epochs: Int, private val seed: Long) : TribuoTask() {
    /**
     * Trains the SGD model.
     * @return The serialized evaluation data.
     */
    override fun execute(): ByteArray {
        return encode(id) { regression: Regression ->
            regression.trainSGD(epochs, seed)
        }
    }
}

/**
 * Request for training the AdaGrad model.
 * @param id unique instance id for the [Regression] tutorial.
 * @param epochs The number of epochs (complete passes through the training data).
 * @param seed A seed for the random number generator, used to shuffle the examples before each epoch.
 */
@Serializable
class TrainAdaGrad(private val id: Int, private val epochs: Int, private val seed: Long) : TribuoTask() {
    /**
     * Trains the AdaGrad model.
     * @return The serialized evaluation data.
     */
    override fun execute(): ByteArray {
        return encode(id) { regression: Regression ->
            regression.trainAdaGrad(epochs, seed)
        }
    }
}

/**
 * Request for training the CART model.
 * @param id unique instance id for the [Regression] tutorial.
 * @param maxDepth The maximum depth of the tree.
 */
@Serializable
class TrainCART(private val id: Int, private val maxDepth: Int) : TribuoTask() {
    /**
     * Trains the CART model.
     * @return The serialized evaluation data.
     */
    override fun execute(): ByteArray {
        return encode(id) { regression: Regression ->
            regression.trainCART(maxDepth)
        }
    }
}

@Serializable
data class RegressionEvaluationResponse(val rmse: Double, val mae: Double, val r2: Double) {
    override fun toString(): String {
        return String.format("Evaluation (test):%n  RMSE %f%n  MAE %f%n  R^2 %f", rmse, mae, r2)
    }
}
