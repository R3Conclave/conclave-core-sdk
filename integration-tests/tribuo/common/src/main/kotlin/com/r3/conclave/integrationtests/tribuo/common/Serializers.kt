package com.r3.conclave.integrationtests.tribuo.common

import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.ByteArraySerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import org.tribuo.Dataset
import org.tribuo.FeatureMap
import org.tribuo.Model
import org.tribuo.classification.Label
import org.tribuo.math.la.DenseVector
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.ObjectInputStream
import java.io.ObjectOutputStream

/**
 * Creates a [SerialDescriptor] of [PrimitiveKind.STRING].
 * @return [SerialDescriptor] for type [T].
 */
inline fun <reified T> serialDescriptor(): SerialDescriptor {
    return PrimitiveSerialDescriptor(T::class.java.canonicalName, PrimitiveKind.STRING)
}

/**
 * Serializer for [DataStatsResponse].
 */
object DataStatsResponseSerializer : KSerializer<DataStatsResponse> {
    override fun deserialize(decoder: Decoder): DataStatsResponse {
        val byteArray = decoder.decodeSerializableValue(ByteArraySerializer())
        ByteArrayInputStream(byteArray).use { bis ->
            ObjectInputStream(bis).use { ois ->
                val trainingDataset = ois.readObject() as Dataset<*>
                val testingDataset = ois.readObject() as Dataset<*>
                return DataStatsResponse(trainingDataset, testingDataset)
            }
        }
    }

    override val descriptor: SerialDescriptor
        get() = serialDescriptor<DataStatsResponse>()

    override fun serialize(encoder: Encoder, value: DataStatsResponse) {
        val byteArrayOutputStream = ByteArrayOutputStream()
        byteArrayOutputStream.use { outputStream ->
            ObjectOutputStream(outputStream).use { oos ->
                oos.writeObject(value.trainingDataset)
                oos.writeObject(value.testingDataset)
            }
        }
        encoder.encodeSerializableValue(ByteArraySerializer(), byteArrayOutputStream.toByteArray())
    }
}

/**
 * Deserializes a single [java.io.Serializable] to an instance of type [T].
 */
inline fun <reified T> deserializeJavaObject(decoder: Decoder): T {
    val byteArray = decoder.decodeSerializableValue(ByteArraySerializer())
    ByteArrayInputStream(byteArray).use { bis ->
        ObjectInputStream(bis).use { ois ->
            return ois.readObject() as T
        }
    }
}

/**
 * Serializes a single [java.io.Serializable] instance of type [T].
 */
inline fun <reified T> serializeJavaObject(encoder: Encoder, value: T) {
    val byteArrayOutputStream = ByteArrayOutputStream()
    byteArrayOutputStream.use { outputStream ->
        ObjectOutputStream(outputStream).use { oos ->
            oos.writeObject(value)
        }
    }
    encoder.encodeSerializableValue(ByteArraySerializer(), byteArrayOutputStream.toByteArray())
}

/**
 * Serializer for [FeatureMap].
 */
object FeatureMapSerializer : KSerializer<FeatureMap> {
    override val descriptor: SerialDescriptor
        get() = serialDescriptor<FeatureMap>()
    override fun deserialize(decoder: Decoder): FeatureMap {
        return deserializeJavaObject(decoder)
    }
    override fun serialize(encoder: Encoder, value: FeatureMap) {
        serializeJavaObject(encoder, value)
    }
}

/**
 * Serializer for [Label].
 */
object LabelSerializer : KSerializer<Label> {
    override val descriptor: SerialDescriptor
        get() = serialDescriptor<Label>()
    override fun deserialize(decoder: Decoder): Label = deserializeJavaObject(decoder)
    override fun serialize(encoder: Encoder, value: Label) = serializeJavaObject(encoder, value)
}

/**
 * Serializer for [ModelWrapper].
 */
object ModelWrapperSerializer : KSerializer<ModelWrapper> {
    override val descriptor: SerialDescriptor
        get() = serialDescriptor<ModelWrapper>()
    override fun serialize(encoder: Encoder, value: ModelWrapper) = serializeJavaObject(encoder, value.value)
    override fun deserialize(decoder: Decoder): ModelWrapper = ModelWrapper(deserializeJavaObject(decoder))
}

/**
 * Serializer for [Model].
 */
object ModelSerializer : KSerializer<Model<*>> {
    override val descriptor: SerialDescriptor
        get() = serialDescriptor<Model<*>>()
    override fun serialize(encoder: Encoder, value: Model<*>) = serializeJavaObject(encoder, value)
    override fun deserialize(decoder: Decoder): Model<*> = deserializeJavaObject(decoder)
}

/**
 * Serializer for [CentroidsResponse].
 */
object CentroidsResponseSerializer : KSerializer<CentroidsResponse> {
    override val descriptor: SerialDescriptor
        get() = serialDescriptor<DenseVector>()
    override fun serialize(encoder: Encoder, value: CentroidsResponse) = serializeJavaObject(encoder, value.centroids)
    override fun deserialize(decoder: Decoder): CentroidsResponse = CentroidsResponse(deserializeJavaObject(decoder))
}