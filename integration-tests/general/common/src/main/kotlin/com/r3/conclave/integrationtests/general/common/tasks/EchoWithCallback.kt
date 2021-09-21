package com.r3.conclave.integrationtests.general.common.tasks

import com.r3.conclave.integrationtests.general.common.EnclaveContext
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.serializer

@Serializable
class EchoWithCallback(val data: ByteArray) : EnclaveTestAction<Unit>() {
    override fun run(context: EnclaveContext, isMail: Boolean) {
        var echoBack = data
        while (true) {
            val response = context.callUntrustedHost(echoBack) ?: return
            echoBack = response
        }
    }

    override fun resultSerializer(): KSerializer<Unit> = Unit.serializer()
}
