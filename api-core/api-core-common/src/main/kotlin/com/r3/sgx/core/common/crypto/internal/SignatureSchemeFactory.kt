package com.r3.sgx.core.common.crypto.internal

import com.r3.sgx.core.common.crypto.SignatureScheme
import com.r3.sgx.core.common.crypto.SignatureSchemeId

object SignatureSchemeFactory {
    fun make(spec: SignatureSchemeId): SignatureScheme {
        require(spec == SignatureSchemeId.EDDSA_ED25519_SHA512)
        return SignatureSchemeEdDSA()
    }
}