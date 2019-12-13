package com.r3.sgx.core.common

enum class EncryptionProtocolId(val tag: Int) {
    /**
     * Key Agreement with ECDH based on ED25519; encryption with AES/GCM 128-bits
     */
    ED25519_AESGCM128(1);

    companion object {
        fun fromInt(value: Int): EncryptionProtocolId? {
            return values().firstOrNull { it.tag == value }
        }
    }
}
