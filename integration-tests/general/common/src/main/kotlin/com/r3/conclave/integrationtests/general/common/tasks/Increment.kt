package com.r3.conclave.integrationtests.general.common.tasks

import com.r3.conclave.integrationtests.general.common.EnclaveContext
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.serializer

@Serializable
class Increment(val data: Int) : EnclaveTestAction<Int>() {
    override fun run(context: EnclaveContext, isMail: Boolean): Int = data + 1

    override fun resultSerializer(): KSerializer<Int> = Int.serializer()
}
