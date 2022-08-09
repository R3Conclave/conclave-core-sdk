package com.r3.conclave.integrationtests.general.common.tasks

import com.r3.conclave.integrationtests.general.common.EnclaveContext
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ByteArraySerializer

/**
 * Action which creates a signed quote with given report data, then returns it.
 */
@Serializable
class CreateAttestationQuoteAction(private val reportData: ByteArray) : EnclaveTestAction<ByteArray>() {
    override fun run(context: EnclaveContext, isMail: Boolean): ByteArray {
        return context.createAttestationQuote(reportData)
    }

    override fun resultSerializer(): KSerializer<ByteArray> = ByteArraySerializer()
}
