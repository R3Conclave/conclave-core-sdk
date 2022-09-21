package com.r3.conclave.common.internal.handler

enum class HostToEnclave {
    OPEN,
    CLOSE,
    PERSISTENCE_KDS_PRIVATE_KEY_RESPONSE
}

enum class EnclaveToHost {
    ENCLAVE_INFO
}
