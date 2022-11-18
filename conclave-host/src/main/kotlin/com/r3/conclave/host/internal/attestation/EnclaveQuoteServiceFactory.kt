package com.r3.conclave.host.internal.attestation

import com.r3.conclave.host.AttestationParameters
import com.r3.conclave.host.internal.QuotingManager

object EnclaveQuoteServiceFactory {
    fun getService(attestationParameters: AttestationParameters?, quotingManager: QuotingManager): EnclaveQuoteService {

        return when (attestationParameters) {
            is AttestationParameters.EPID -> EnclaveQuoteServiceEPID(attestationParameters)
            is AttestationParameters.DCAP -> when (quotingManager) {
                QuotingManager.HOST -> EnclaveQuoteServiceDCAP()
                QuotingManager.ENCLAVE -> EnclaveQuoteServiceGramineDCAP()
            }

            null -> EnclaveQuoteServiceMock
        }
    }
}
