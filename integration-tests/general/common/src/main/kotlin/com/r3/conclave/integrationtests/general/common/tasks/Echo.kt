package com.r3.conclave.integrationtests.general.common.tasks

import com.r3.conclave.integrationtests.general.common.EnclaveContext
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ByteArraySerializer

@Serializable
class Echo(val data: ByteArray) : EnclaveTestAction<ByteArray>() {
    override fun run(context: EnclaveContext, isMail: Boolean): ByteArray = data

    override fun resultSerializer(): KSerializer<ByteArray> = ByteArraySerializer()
}
