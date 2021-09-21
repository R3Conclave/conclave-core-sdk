package com.r3.conclave.integrationtests.general.common.tasks

import com.r3.conclave.integrationtests.general.common.EnclaveContext
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.serializer
import java.util.concurrent.atomic.AtomicInteger

// Check that if we don't opt-in to multi-threading, the host cannot force us to run in parallel, including across
// re-entrancy points (calls back into the host).
@Serializable
class CheckNotMultiThreadedAction : EnclaveTestAction<Unit>() {
    override fun createNewState() = State()

    override fun run(context: EnclaveContext, isMail: Boolean) {
        val state = context.stateAs<State>()
        val atomicInteger = if (isMail) state.mailCalls else state.hostCalls
        check(atomicInteger) {
            context.callUntrustedHost(byteArrayOf())
        }
    }

    private fun check(atomicInteger: AtomicInteger, block: () -> Unit) {
        val x = atomicInteger.incrementAndGet()
        if (x > 1)
            throw IllegalStateException("All calls should be serialized by default: $x")
        Thread.sleep(100)
        block()
        atomicInteger.decrementAndGet()
    }

    override fun resultSerializer(): KSerializer<Unit> = Unit.serializer()

    class State {
        val hostCalls = AtomicInteger(0)
        val mailCalls = AtomicInteger(0)
    }
}
