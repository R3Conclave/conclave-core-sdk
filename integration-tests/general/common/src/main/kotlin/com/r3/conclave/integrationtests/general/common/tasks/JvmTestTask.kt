package com.r3.conclave.integrationtests.general.common.tasks

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic

/**
 * The following code registers the subclasses of [JvmTestTask] for polymorphic
 * serialization, meaning the enclave will create instances of the subclass
 * when it deserializes a [JvmTestTask]. This allows usage of polymorphism to
 * run each [JvmTestTask]'s logic.
 */
val messageModule = SerializersModule {
    polymorphic(JvmTestTask::class) {
        subclass(AvianTestRunner::class, AvianTestRunner.serializer())

        subclass(Adder::class, Adder.serializer())
        subclass(Echo::class, Echo.serializer())
        subclass(EchoWithCallback::class, EchoWithCallback.serializer())
        subclass(Increment::class, Increment.serializer())
        subclass(Outliers::class, Outliers.serializer())
        subclass(RepeatedOcall::class, RepeatedOcall.serializer())
        subclass(Recursing::class, Recursing.serializer())
        subclass(SetInt::class, SetInt.serializer())
        subclass(Signer::class, Signer.serializer())
        subclass(Spin::class, Spin.serializer())
        subclass(Sum1ToN::class, Sum1ToN.serializer())
        subclass(Thrower::class, Thrower.serializer())
        subclass(Wait::class, Wait.serializer())
        subclass(WithState::class, WithState.serializer())
    }
}

val format = Json { serializersModule = messageModule }

/**
 * Deserializes a JSON response to and instance of its expected type [R]
 * @param encoded Serialized response as a [ByteArray]
 * @return Instance of the response type
 */
@OptIn(ExperimentalSerializationApi::class)
inline fun <reified R> decode(encoded: ByteArray): R {
    return format.decodeFromString(String(encoded))
}

/**
 * This class abstracts the [JvmTestTask]s to be sent to the enclave,
 * containing the execution scenario of each test.
 */
@Serializable
abstract class JvmTestTask {
    /**
     * Runs the logic associated with each [JvmTestTask].
     * @return Serialized JSON response as a [ByteArray]
     */
    abstract fun run(context: RuntimeContext): ByteArray

    /**
     * Serializes a [JvmTestTask] to a JSON string
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
     * Deserializes a response to the corresponding [JvmTestTask] response type
     * @param encoded Serialized response as a [ByteArray]
     * @return An instance of the response type
     */
    fun deserialize(encoded: ByteArray): R
}

