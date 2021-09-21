package com.r3.conclave.integrationtests.general.common.tasks

import com.r3.conclave.common.EnclaveInstanceInfo
import com.r3.conclave.integrationtests.general.common.EnclaveContext
import kotlinx.serialization.*
import kotlinx.serialization.builtins.ByteArraySerializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic
import kotlinx.serialization.protobuf.ProtoBuf

@OptIn(ExperimentalSerializationApi::class)
private val protoBuf = ProtoBuf {
    serializersModule = SerializersModule {
        polymorphic(EnclaveTestAction::class) {
            subclass(AvianTestRunner::class, AvianTestRunner.serializer())
            subclass(ConcurrentCallsIntoEnclaveAction::class, ConcurrentCallsIntoEnclaveAction.serializer())
            subclass(Echo::class, Echo.serializer())
            subclass(EchoWithCallback::class, EchoWithCallback.serializer())
            subclass(Increment::class, Increment.serializer())
            subclass(Outliers::class, Outliers.serializer())
            subclass(RepeatedOcallsAction::class, RepeatedOcallsAction.serializer())
            subclass(EcallOcallRecursionAction::class, EcallOcallRecursionAction.serializer())
            subclass(SetMaxCallCount::class, SetMaxCallCount.serializer())
            subclass(SigningAction::class, SigningAction.serializer())
            subclass(SpinAction::class, SpinAction.serializer())
            subclass(TooManyThreadsRequestedAction::class, TooManyThreadsRequestedAction.serializer())
            subclass(Thrower::class, Thrower.serializer())
            subclass(TcsReallocationAction::class, TcsReallocationAction.serializer())
            subclass(StatefulAction::class, StatefulAction.serializer())
            subclass(CreatePostOffice::class, CreatePostOffice.serializer())
            subclass(GetSecretKey::class, GetSecretKey.serializer())
            subclass(GetEnclaveInstanceInfo::class, GetEnclaveInstanceInfo.serializer())
            subclass(CheckNotMultiThreadedAction::class, CheckNotMultiThreadedAction.serializer())
            subclass(SealData::class, SealData.serializer())
            subclass(UnsealData::class, UnsealData.serializer())
        }
    }
}

@OptIn(ExperimentalSerializationApi::class)
fun <T> encode(serializer: SerializationStrategy<T>, value: T): ByteArray {
    return protoBuf.encodeToByteArray(serializer, value)
}

@OptIn(ExperimentalSerializationApi::class)
fun <T> decode(deserializer: DeserializationStrategy<T>, bytes: ByteArray): T {
    return protoBuf.decodeFromByteArray(deserializer, bytes)
}

object EnclaveInstanceInfoSerializer : KSerializer<EnclaveInstanceInfo> {
    @OptIn(ExperimentalSerializationApi::class)
    override val descriptor = SerialDescriptor("EnclaveInstanceInfo", ByteArraySerializer().descriptor)

    override fun serialize(encoder: Encoder, value: EnclaveInstanceInfo) {
        encoder.encodeSerializableValue(ByteArraySerializer(), value.serialize())
    }

    override fun deserialize(decoder: Decoder): EnclaveInstanceInfo {
        val bytes = decoder.decodeSerializableValue(ByteArraySerializer())
        return EnclaveInstanceInfo.deserialize(bytes)
    }
}

/**
 * Represents an aribitary action that is "sent" to the enclave to be actioned. This enables one enclave class to
 * perform multiple different tasks.
 *
 * This is a workaround to the limitation of native image in non-mock mode, as building such an enclave takes time and
 * having a seperate enclave class for each test makes the integration test run time incredibly long.
 */
@Serializable
abstract class EnclaveTestAction<R> {
    /**
     * Override this method to return a custom state object if you need to have in-memory state that's visible across
     * multiple actions. The state is available via [EnclaveContext.stateAs].
     */
    open fun createNewState(): Any? = null

    /**
     * This is executed inside the enclave's `receiveFromUntrustedHost`. The result value is serialised and returned.
     * The action can also be sent in a mail and received by the encalve via `receiveMail`. The result value in this
     * case is serialised and encrypted in a response mail back to the sender.
     */
    abstract fun run(context: EnclaveContext, isMail: Boolean): R

    abstract fun resultSerializer(): KSerializer<R>
}
