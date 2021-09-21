package com.r3.conclave.integrationtests.general.common.tasks

import com.r3.conclave.integrationtests.general.common.EnclaveContext
import com.r3.conclave.integrationtests.general.common.PlaintextAndAD
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ByteArraySerializer

@Serializable
class SealData(private val data: PlaintextAndAD) : EnclaveTestAction<ByteArray>() {
    override fun run(context: EnclaveContext, isMail: Boolean): ByteArray = context.sealData(data)

    override fun resultSerializer(): KSerializer<ByteArray> = ByteArraySerializer()
}
