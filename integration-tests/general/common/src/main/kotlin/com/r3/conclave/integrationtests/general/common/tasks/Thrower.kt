package com.r3.conclave.integrationtests.general.common.tasks

import com.r3.conclave.integrationtests.general.common.EnclaveContext
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.serializer

@Serializable
class Thrower : EnclaveTestAction<Unit>() {
    companion object {
        const val CHEERS = "You are all wrong"
    }

    override fun run(context: EnclaveContext, isMail: Boolean) {
        throw RuntimeException(CHEERS)
    }

    override fun resultSerializer(): KSerializer<Unit> = Unit.serializer()
}
