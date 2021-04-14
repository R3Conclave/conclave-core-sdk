package com.r3.conclave.integrationtests.jni.tasks

import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic

/**
 * The following code registers the subclasses of [GraalJniTask] for polymorphic
 * serialization, meaning the enclave will create instances of the subclass
 * when it deserializes a [GraalJniTask]. This allows usage of polymorphism to
 * run each [GraalJniTask]'s logic.
 */
val messageModule = SerializersModule {
    polymorphic(GraalJniTask::class) {
        subclass(Open::class, Open.serializer())
        subclass(Close::class, Close.serializer())
        subclass(Write::class, Write.serializer())
        subclass(XStat64::class, XStat64.serializer())
        subclass(Time::class, Time.serializer())
    }
}

val format = Json { serializersModule = messageModule }

/**
 * Deserializes a JSON response to and instance of its expected type [R]
 * @param encoded Serialized response as a [ByteArray]
 * @return Instance of the response type
 */
inline fun <reified R> decode(encoded: ByteArray): R {
    return format.decodeFromString(String(encoded))
}

/**
 * This class abstracts the [GraalJniTask]s to be sent to the enclave,
 * containing the execution scenario of each test.
 */
@Serializable
abstract class GraalJniTask {
    /**
     * Runs the logic associated with each [GraalJniTask].
     * @return Serialized JSON response as a [ByteArray]
     */
    abstract fun run(): ByteArray

    /**
     * Serializes a [GraalJniTask] to a JSON string
     */
    fun encode(): ByteArray {
        return format.encodeToString(serializer(), this).toByteArray()
    }
}

/**
 * This interface forces its subclasses to pass the response type as [R],
 * for type safety when receiving responses from the enclave.
 */
interface Deserializer<out R> {
    /**
     * Deserializes a response to the corresponding [GraalJniTask] response type
     * @param encoded Serialized response as a [ByteArray]
     * @return An instance of the response type
     */
    fun deserialize(encoded: ByteArray): R
}
