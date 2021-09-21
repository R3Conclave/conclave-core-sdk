package com.r3.conclave.integrationtests.general.common.tasks

import com.r3.conclave.common.internal.Cursor
import com.r3.conclave.common.internal.SgxKeyRequest
import com.r3.conclave.integrationtests.general.common.EnclaveContext
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ByteArraySerializer

@Serializable
class GetSecretKey(private val keyRequest: ByteArray) : EnclaveTestAction<ByteArray>() {
    override fun run(context: EnclaveContext, isMail: Boolean): ByteArray {
        return context.getSecretKey(Cursor.wrap(SgxKeyRequest.INSTANCE, keyRequest, 0, keyRequest.size))
    }

    override fun resultSerializer(): KSerializer<ByteArray> = ByteArraySerializer()
}
