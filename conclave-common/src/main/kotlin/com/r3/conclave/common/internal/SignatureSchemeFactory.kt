package com.r3.conclave.common.internal

import com.r3.conclave.common.internal.SignatureScheme
import com.r3.conclave.common.internal.SignatureSchemeEdDSA
import com.r3.conclave.common.internal.SignatureSchemeId

object SignatureSchemeFactory {
    fun make(spec: SignatureSchemeId): SignatureScheme {
        require(spec == SignatureSchemeId.EDDSA_ED25519_SHA512)
        return SignatureSchemeEdDSA()
    }
}