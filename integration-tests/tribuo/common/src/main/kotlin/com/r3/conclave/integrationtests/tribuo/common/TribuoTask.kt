package com.r3.conclave.integrationtests.tribuo.common

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationStrategy
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic
import org.tribuo.FeatureMap
import org.tribuo.Model
import org.tribuo.classification.Label
import java.util.concurrent.atomic.AtomicInteger

/**
 * The following code registers the subclasses of [TribuoTask] for polymorphic
 * serialization, meaning the enclave will create instances of the subclass
 * when it deserializes a [TribuoTask]. This allows usage of polymorphism to
 * run each [TribuoTask]'s logic.
 */
val messageModule = SerializersModule {
    polymorphic(TribuoTask::class) {
        subclass(ClassificationInitializeImpl::class, ClassificationInitializeImpl.serializer())
        subclass(DataStats::class, DataStats.serializer())
        subclass(TrainerInfo::class, TrainerInfo.serializer())
        subclass(TrainAndEvaluate::class, TrainAndEvaluate.serializer())
        subclass(ConfusionMatrix::class, ConfusionMatrix.serializer())
        subclass(SerializedModel::class, SerializedModel.serializer())
        subclass(LoadModel::class, LoadModel.serializer())
        // File tasks
        subclass(EnclaveFile::class, EnclaveFile.serializer())
        subclass(DeleteFile::class, DeleteFile.serializer())
        // Clustering tasks
        subclass(ClusteringInitializeImpl::class, ClusteringInitializeImpl.serializer())
        subclass(Train::class, Train.serializer())
        subclass(Centroids::class, Centroids.serializer())
        subclass(Evaluate::class, Evaluate.serializer())
        // Regression tasks
        subclass(InitializeRegression::class, InitializeRegression.serializer())
        subclass(TrainSGD::class, TrainSGD.serializer())
        subclass(TrainAdaGrad::class, TrainAdaGrad.serializer())
        subclass(TrainCART::class, TrainCART.serializer())
        // Anomaly detection tasks
        subclass(AnomalyDetection.InitializeAnomalyDetection::class, AnomalyDetection.InitializeAnomalyDetection.serializer())
        subclass(AnomalyDetection.TrainAndEvaluate::class, AnomalyDetection.TrainAndEvaluate.serializer())
        subclass(AnomalyDetection.ConfusionMatrix::class, AnomalyDetection.ConfusionMatrix.serializer())
        // Configuration tasks
        subclass(Configuration.InitializeConfiguration::class, Configuration.InitializeConfiguration.serializer())
        subclass(Configuration.DataStats::class, Configuration.DataStats.serializer())
        subclass(Configuration.InitializeLogisticTrainer::class, Configuration.InitializeLogisticTrainer.serializer())
        subclass(Configuration.MnistLogisticConfig::class, Configuration.MnistLogisticConfig.serializer())
        subclass(Configuration.LrEvaluator::class, Configuration.LrEvaluator.serializer())
        subclass(Configuration.LrEvaluatorConfusionMatrix::class, Configuration.LrEvaluatorConfusionMatrix.serializer())
        subclass(Configuration.NewEvaluator::class, Configuration.NewEvaluator.serializer())
        subclass(Configuration.NewEvaluatorConfusionMatrix::class, Configuration.NewEvaluatorConfusionMatrix.serializer())
        subclass(Configuration.NewEvaluatorProvenance::class, Configuration.NewEvaluatorProvenance.serializer())
        subclass(Configuration.TransformedEvaluator::class, Configuration.TransformedEvaluator.serializer())
        subclass(Configuration.TransformedEvaluatorConfusionMatrix::class, Configuration.TransformedEvaluatorConfusionMatrix.serializer())
        subclass(Configuration.MnistTransformedLogisticConfig::class, Configuration.MnistTransformedLogisticConfig.serializer())
    }
    contextual(FeatureMap::class, FeatureMapSerializer)
    contextual(Label::class, LabelSerializer)
    contextual(Model::class, ModelSerializer)
}

val format = Json { serializersModule = messageModule }

/**
 * Deserializes a response to an instance of its expected type [R].
 * @param encoded Serialized response as a [ByteArray].
 * @return Instance of the response type.
 */
inline fun <reified R> decode(encoded: ByteArray): R {
    return format.decodeFromString(String(encoded))
}

/**
 * Obtains the [TribuoObject] instance from [TribuoObject.objects] on which to
 * execute the [function].
 * @param id The id of the [TribuoObject]'s instance to obtain.
 * @param function The function to be executed on the [TribuoObject]'s instance.
 * @return The serialized [function]'s return value serialized to a [ByteArray].
 */
inline fun <T, reified R> encode(id: Int, function: (T) -> R): ByteArray {
    @Suppress("UNCHECKED_CAST") val obj = TribuoObject.objects[id] as T
    return Json.encodeToString(function(obj)).toByteArray()
}

inline fun <T, reified R> encode(id: Int, serializer: SerializationStrategy<R>, function: (T) -> R): ByteArray {
    @Suppress("UNCHECKED_CAST") val obj = TribuoObject.objects[id] as T
    return Json.encodeToString(serializer, function(obj)).toByteArray()
}

/**
 * This class abstracts the [TribuoTask]s to be executed in the enclave.
 */
@Serializable
abstract class TribuoTask {
    /**
     * Executes the logic associated with each [TribuoTask].
     * @return Serialized response as a [ByteArray].
     */
    abstract fun execute(): ByteArray

    /**
     * Serializes a [TribuoTask].
     * @return The serialized [TribuoTask] as a [ByteArray].
     */
    fun encode(): ByteArray {
        return format.encodeToString(serializer(), this).toByteArray()
    }
}

/**
 * This class abstracts the enclave instances of the Tribuo tutorials,
 * i.e., Classification, Clustering, Regression, etc..
 * The client uses the [id] returned by the enclave to invoke methods
 * on the enclave's instances.
 * @param id unique id to be used by the client when remotely invoking
 * methods on the enclave's tutorials instances.
 */
abstract class TribuoObject(val id: Int) : TribuoTask() {
    companion object {
        /**
         * The next available unique id
         */
        var id = AtomicInteger(0)

        /**
         * Map of instances created in the enclave.
         * When the client remotely initializes a tutorial the enclave will
         * store the instance in this map and refer to it for the client's
         * remote procedure calls.
         */
        val objects = HashMap<Int, TribuoTask>()

        /**
         * Initializes the [TribuoObject] and adds it to [objects].
         * @param function The [TribuoObject]'s initialization function.
         * @return The unique [id] of the [TribuoObject] instance.
         */
        @Synchronized
        fun <T: TribuoObject> encode(function: () -> T): ByteArray {
            val t = function()
            objects[t.id] = t
            return t.execute()
        }
    }

    /**
     * Serializes the unique [id] of the [TribuoObject].
     * @return The serialized [id] as a [ByteArray]
     */
    override fun execute(): ByteArray {
        return Json.encodeToString(id).toByteArray()
    }
}
