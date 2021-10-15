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

/**
 * Represents an arbitrary action that is "sent" to the enclave to be actioned. This enables one enclave class to
 * perform multiple different tasks.
 *
 * This is a workaround to the limitation of native image in non-mock mode, as building such an enclave takes time and
 * having a separate enclave class for each test makes the integration test run time incredibly long.
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
     * The action can also be sent in a mail and received by the enclave via `receiveMail`. The result value in this
     * case is serialised and encrypted in a response mail back to the sender.
     */
    abstract fun run(context: EnclaveContext, isMail: Boolean): R

    abstract fun resultSerializer(): KSerializer<R>
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
            subclass(PutPersistentMap::class, PutPersistentMap.serializer())
            subclass(GetPersistentMap::class, GetPersistentMap.serializer())
            subclass(PathsGet::class, PathsGet.serializer())
            subclass(FilesWrite::class, FilesWrite.serializer())
            subclass(DeleteFile::class, DeleteFile.serializer())
            subclass(FilesCreateDirectory::class, FilesCreateDirectory.serializer())
            subclass(FilesCreateDirectories::class, FilesCreateDirectories.serializer())
            subclass(FilesExists::class, FilesExists.serializer())
            subclass(FilesSize::class, FilesSize.serializer())
            subclass(FilesReadAllBytes::class, FilesReadAllBytes.serializer())
            subclass(NewDeleteOnCloseOutputStream::class, NewDeleteOnCloseOutputStream.serializer())
            subclass(NewInputStream::class, NewInputStream.serializer())
            subclass(ReadByteFromInputStream::class, ReadByteFromInputStream.serializer())
            subclass(ReadBytesFromInputStream::class, ReadBytesFromInputStream.serializer())
            subclass(ReadAllBytesFromInputStream::class, ReadAllBytesFromInputStream.serializer())
            subclass(IsInputStreamMarkSupported::class, IsInputStreamMarkSupported.serializer())
            subclass(CloseInputStream::class, CloseInputStream.serializer())
            subclass(ResetInputStream::class, ResetInputStream.serializer())
            subclass(IsFileInputStreamFDValid::class, IsFileInputStreamFDValid.serializer())
            subclass(OpenUrlFileInputStream::class, OpenUrlFileInputStream.serializer())
            subclass(NewFileOuputStream::class, NewFileOuputStream.serializer())
            subclass(WriteByteToOuputStream::class, WriteByteToOuputStream.serializer())
            subclass(WriteBytesToOuputStream::class, WriteBytesToOuputStream.serializer())
            subclass(WriteOffsetBytesToOuputStream::class, WriteOffsetBytesToOuputStream.serializer())
            subclass(CloseOuputStream::class, CloseOuputStream.serializer())
            subclass(NewDeleteOnCloseByteChannel::class, NewDeleteOnCloseByteChannel.serializer())
            subclass(CloseByteChannel::class, CloseByteChannel.serializer())
            subclass(NewBufferedFileInputStream::class, NewBufferedFileInputStream.serializer())
            subclass(ReadAndWriteFiles::class, ReadAndWriteFiles.serializer())
            subclass(WriteFilesConcurrently::class, WriteFilesConcurrently.serializer())
            subclass(RandomAccessFileConcurrentWrites::class, RandomAccessFileConcurrentWrites.serializer())
        }
    }
}
