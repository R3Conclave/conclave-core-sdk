package com.r3.conclave.common.internal.handler

enum class HostToEnclave {
    ATTESTATION,
    OPEN,
    CLOSE,
    PERSISTENCE_KDS_KEY_SPEC_REQUEST,
    PERSISTENCE_KDS_PRIVATE_KEY_RESPONSE
}

enum class EnclaveToHost {
    ENCLAVE_INFO,
    ATTESTATION,
    PERSISTENCE_KDS_KEY_SPEC_RESPONSE,
}
