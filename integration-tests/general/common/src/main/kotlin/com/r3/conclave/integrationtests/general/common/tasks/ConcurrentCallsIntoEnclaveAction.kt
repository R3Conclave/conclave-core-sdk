package com.r3.conclave.integrationtests.general.common.tasks

import com.r3.conclave.integrationtests.general.common.EnclaveContext
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.serializer
import java.util.concurrent.atomic.AtomicInteger

@Serializable
class ConcurrentCallsIntoEnclaveAction(private val number: Int) : EnclaveTestAction<Int>() {
    override fun run(context: EnclaveContext, isMail: Boolean): Int {
        val state = context.stateAs<AddingState>()
        val sum = state.sum.addAndGet(number)
        return if (state.callCount.incrementAndGet() == state.maxCallCount) {
            sum
        } else {
            0
        }
    }

    override fun resultSerializer(): KSerializer<Int> = Int.serializer()
}

@Serializable
class SetMaxCallCount(private val value: Int) : EnclaveTestAction<Unit>() {
    override fun createNewState() = AddingState()

    override fun run(context: EnclaveContext, isMail: Boolean) {
        context.stateAs<AddingState>().maxCallCount = value
    }

    override fun resultSerializer(): KSerializer<Unit> = Unit.serializer()
}

class AddingState {
    var maxCallCount = 0
    val sum = AtomicInteger(0)
    val callCount = AtomicInteger(0)
}
