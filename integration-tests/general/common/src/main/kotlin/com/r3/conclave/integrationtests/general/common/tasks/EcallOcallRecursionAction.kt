package com.r3.conclave.integrationtests.general.common.tasks

import com.r3.conclave.integrationtests.general.common.EnclaveContext
import com.r3.conclave.integrationtests.general.common.toByteArray
import com.r3.conclave.integrationtests.general.common.toInt
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.serializer

@Serializable
class EcallOcallRecursionAction(private var remaining: Int) : EnclaveTestAction<Unit>() {
    override fun run(context: EnclaveContext, isMail: Boolean) {
        while (remaining > 0) {
            remaining = context.callUntrustedHost((remaining - 1).toByteArray())!!.toInt()
        }
    }

    override fun resultSerializer(): KSerializer<Unit> = Unit.serializer()
}
