package com.r3.conclave.integrationtests.general.common.tasks

import com.r3.conclave.integrationtests.general.common.EnclaveContext
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.serializer

@Serializable
class PutPersistentMap(val key: String, val value: ByteArray) : EnclaveTestAction<Unit>() {
    override fun run(context: EnclaveContext, isMail: Boolean) {
        context.persistentMap[key] = value
    }

    override fun resultSerializer(): KSerializer<Unit> = Unit.serializer()
}
