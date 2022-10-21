package com.r3.conclave.integrationtests.general.common.tasks

import com.r3.conclave.integrationtests.general.common.EnclaveContext
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.serializer
import java.net.ServerSocket

@Serializable
class CreateSocket : EnclaveTestAction<Unit>() {
    override fun run(context: EnclaveContext, isMail: Boolean) {

        val server = ServerSocket(9999)
        val socket = server.accept()

    }

    override fun resultSerializer(): KSerializer<Unit> = Unit.serializer()
}
