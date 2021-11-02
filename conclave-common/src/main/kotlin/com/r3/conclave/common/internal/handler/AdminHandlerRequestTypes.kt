package com.r3.conclave.common.internal.handler

class AdminHandlerRequestTypes {
    enum class Host {
        ENCLAVE_INFO,
        ATTESTATION,
        KDS_KEY_SPECIFICATION
    }
    enum class Enclave {
        ATTESTATION,
        OPEN,
        CLOSE,
        KDS_KEY_SPECIFICATION,
        KDS_RESPONSE
    }
}