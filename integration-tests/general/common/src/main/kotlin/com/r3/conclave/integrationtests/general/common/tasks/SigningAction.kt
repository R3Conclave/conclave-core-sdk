package com.r3.conclave.integrationtests.general.common.tasks

import com.r3.conclave.integrationtests.general.common.EnclaveContext
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable

@Serializable
class SigningAction(val data: ByteArray) : EnclaveTestAction<KeyAndSignature>() {
    override fun run(context: EnclaveContext, isMail: Boolean): KeyAndSignature {
        val signature = context.signer().run {
            update(data)
            sign()
        }
        return KeyAndSignature(context.signatureKey.encoded, signature)
    }

    override fun resultSerializer(): KSerializer<KeyAndSignature> = KeyAndSignature.serializer()
}

@Serializable
class KeyAndSignature(val encodedPublicKey: ByteArray, val signature: ByteArray)
