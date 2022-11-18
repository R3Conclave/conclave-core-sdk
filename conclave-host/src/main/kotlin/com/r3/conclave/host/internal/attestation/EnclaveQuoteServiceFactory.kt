package com.r3.conclave.host.internal.attestation

import com.r3.conclave.host.AttestationParameters

object EnclaveQuoteServiceFactory {
    fun getService(attestationParameters: AttestationParameters?): EnclaveQuoteService {
        return when (attestationParameters) {
            is AttestationParameters.EPID -> EnclaveQuoteServiceEPID(attestationParameters)
            is AttestationParameters.DCAP -> EnclaveQuoteServiceDCAP()
            null -> EnclaveQuoteServiceMock
        }
    }
}
