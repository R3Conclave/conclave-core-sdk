package com.r3.conclave.integrationtests.general.common.tasks

import com.r3.conclave.integrationtests.general.common.EnclaveContext
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.serializer

@Serializable
class StatefulAction(val bytes: ByteArray) : EnclaveTestAction<String>() {
    override fun createNewState() = State()

    override fun run(context: EnclaveContext, isMail: Boolean): String {
        val state = context.stateAs<State>()
        val builder = StringBuilder(state.previousResult)
        for (index in bytes) {
            val lookupValue = context.callUntrustedHost(byteArrayOf(index))!!
            builder.append(String(lookupValue))
        }
        val result = builder.toString()
        state.previousResult = result
        return result
    }

    override fun resultSerializer(): KSerializer<String> = String.serializer()

    class State {
        var previousResult = ""
    }
}