package com.r3.conclave.integrationtests.general.common.tasks

import com.r3.conclave.integrationtests.general.common.EnclaveContext
import com.r3.conclave.integrationtests.general.common.PlaintextAndAD
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable

@Serializable
class UnsealData(private val sealedBlob: ByteArray) : EnclaveTestAction<PlaintextAndAD>() {
    override fun run(context: EnclaveContext, isMail: Boolean): PlaintextAndAD = context.unsealData(sealedBlob)

    override fun resultSerializer(): KSerializer<PlaintextAndAD> = PlaintextAndAD.serializer()
}
