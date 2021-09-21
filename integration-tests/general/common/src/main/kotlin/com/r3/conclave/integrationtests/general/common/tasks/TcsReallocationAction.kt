package com.r3.conclave.integrationtests.general.common.tasks

import com.r3.conclave.integrationtests.general.common.EnclaveContext
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.serializer
import java.util.concurrent.atomic.AtomicInteger

@Serializable
class TcsReallocationAction(val data: ByteArray) : EnclaveTestAction<Unit>() {
    companion object {
        const val PARALLEL_ECALLS = 8
    }

    override fun createNewState() = State()

    override fun run(context: EnclaveContext, isMail: Boolean) {
        val state = context.stateAs<State>()
        state.ecalls.incrementAndGet()
        while (state.ecalls.get() < PARALLEL_ECALLS) {
            // wait
        }
        synchronized(context) {
            context.callUntrustedHost(data)
        }
    }

    override fun resultSerializer(): KSerializer<Unit> = Unit.serializer()

    class State {
        val ecalls = AtomicInteger(0)
    }
}
