package com.r3.conclave.integrationtests.general.common.tasks

import com.r3.conclave.common.EnclaveInstanceInfo
import com.r3.conclave.integrationtests.general.common.EnclaveContext
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable

@Serializable
class GetEnclaveInstanceInfo : EnclaveTestAction<EnclaveInstanceInfo>() {
    override fun run(context: EnclaveContext, isMail: Boolean): EnclaveInstanceInfo = context.enclaveInstanceInfo

    override fun resultSerializer(): KSerializer<EnclaveInstanceInfo> = EnclaveInstanceInfoSerializer
}
