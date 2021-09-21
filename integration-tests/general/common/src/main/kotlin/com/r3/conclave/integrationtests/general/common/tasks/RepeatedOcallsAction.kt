package com.r3.conclave.integrationtests.general.common.tasks

import com.r3.conclave.integrationtests.general.common.EnclaveContext
import com.r3.conclave.integrationtests.general.common.toByteArray
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.serializer

@Serializable
class RepeatedOcallsAction(private val count: Int) : EnclaveTestAction<Unit>() {
    override fun run(context: EnclaveContext, isMail: Boolean) {
        repeat(count) { index ->
            context.callUntrustedHost(index.toByteArray())
        }
    }

    override fun resultSerializer(): KSerializer<Unit> = Unit.serializer()
}
