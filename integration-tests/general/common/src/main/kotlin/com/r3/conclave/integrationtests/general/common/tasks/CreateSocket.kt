package com.r3.conclave.integrationtests.general.common.tasks

import com.r3.conclave.integrationtests.general.common.EnclaveContext
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.serializer
import java.net.ServerSocket

@Serializable
class CreateSocket(private val port: Int) : EnclaveTestAction<Unit>() {
    override fun run(context: EnclaveContext, isMail: Boolean) {
        ServerSocket(port)
    }

    override fun resultSerializer(): KSerializer<Unit> = Unit.serializer()
}

