package com.r3.conclave.host.internal.attestation

import com.r3.conclave.host.AttestationParameters
import com.r3.conclave.host.internal.EnclaveHandle
import com.r3.conclave.host.internal.NativeEnclaveHandle

object EnclaveQuoteServiceFactory {
    fun getService(attestationParameters: AttestationParameters?, enclaveHandle: EnclaveHandle): EnclaveQuoteService {
        return when (attestationParameters) {
            is AttestationParameters.EPID -> EnclaveQuoteServiceEPID(attestationParameters)
            is AttestationParameters.DCAP -> retrieveDCAPService(enclaveHandle)
            null -> EnclaveQuoteServiceMock
        }
    }

    private fun retrieveDCAPService(enclaveHandle: EnclaveHandle): EnclaveQuoteService {
        return if (enclaveHandle is NativeEnclaveHandle) EnclaveQuoteServiceDCAP() else EnclaveQuoteServiceGramineDCAP()
    }
}