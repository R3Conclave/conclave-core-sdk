package com.r3.conclave.integrationtests.general.common.tasks

import com.r3.conclave.integrationtests.general.common.EnclaveContext
import com.r3.conclave.integrationtests.general.common.tasks.GetPersistentMap.NullableBytes
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable

@Serializable
class GetPersistentMap(val key: String) : EnclaveTestAction<NullableBytes>() {
    override fun run(context: EnclaveContext, isMail: Boolean): NullableBytes {
        return NullableBytes(context.persistentMap[key])
    }

    override fun resultSerializer(): KSerializer<NullableBytes> = NullableBytes.serializer()

    @Serializable
    class NullableBytes(val value: ByteArray?)
}
