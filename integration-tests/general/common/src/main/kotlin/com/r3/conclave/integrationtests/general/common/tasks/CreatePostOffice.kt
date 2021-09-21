package com.r3.conclave.integrationtests.general.common.tasks

import com.r3.conclave.integrationtests.general.common.EnclaveContext
import com.r3.conclave.mail.PostOffice
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.serializer

@Serializable
class CreatePostOffice(private val source: String) : EnclaveTestAction<Unit>() {
    override fun run(context: EnclaveContext, isMail: Boolean) {
        when (source) {
            "PostOffice.create()" -> PostOffice.create(context.enclaveInstanceInfo.encryptionKey)
            "EnclaveInstanceInfo.createPostOffice()" -> context.enclaveInstanceInfo.createPostOffice()
            else -> throw IllegalArgumentException(source)
        }
    }

    override fun resultSerializer(): KSerializer<Unit> = Unit.serializer()
}
