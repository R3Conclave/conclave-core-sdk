package com.r3.conclave.common.internal.handler

enum class HostToEnclave {
    ATTESTATION,
    OPEN,
    CLOSE,
    KDS_KEY_SPEC_REQUEST,
    KDS_RESPONSE
}

enum class EnclaveToHost {
    ENCLAVE_INFO,
    ATTESTATION,
    KDS_KEY_SPEC_RESPONSE
}
