package com.r3.conclave.host.internal.attestation

import com.r3.conclave.host.AttestationParameters
import com.r3.conclave.host.internal.EnclaveHandle
import com.r3.conclave.host.internal.NativeEnclaveHandle
import com.r3.conclave.host.internal.gramine.GramineEnclaveHandle

object EnclaveQuoteServiceFactory {
    fun getService(attestationParameters: AttestationParameters?, enclaveHandle: EnclaveHandle): EnclaveQuoteService {
        return when (attestationParameters) {
            is AttestationParameters.EPID -> EnclaveQuoteServiceEPID(attestationParameters)
            //  Note that in Gramine the Signed Quote is automatically retrieved by the Enclave
            //  So we do not need/use the EnclaveQuoteServiceDCAP and the related Enclave-Host call is
            //      not executed in Gramine
            is AttestationParameters.DCAP -> EnclaveQuoteServiceDCAP()
            null -> EnclaveQuoteServiceMock
        }
    }

    private fun retrieveDCAPService(enclaveHandle: EnclaveHandle): EnclaveQuoteService {
        return if (enclaveHandle is NativeEnclaveHandle) EnclaveQuoteServiceDCAP() else EnclaveGramineQuoteServiceDCAP()
    }
}