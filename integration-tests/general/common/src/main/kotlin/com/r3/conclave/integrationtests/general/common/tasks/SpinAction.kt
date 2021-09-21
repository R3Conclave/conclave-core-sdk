package com.r3.conclave.integrationtests.general.common.tasks

import com.r3.conclave.integrationtests.general.common.EnclaveContext
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.serializer

@Serializable
class SpinAction : EnclaveTestAction<Unit>() {
    override fun run(context: EnclaveContext, isMail: Boolean) {
        context.callUntrustedHost(byteArrayOf())
        while (true) {
            // Spin
        }
    }

    override fun resultSerializer(): KSerializer<Unit> = Unit.serializer()
}
