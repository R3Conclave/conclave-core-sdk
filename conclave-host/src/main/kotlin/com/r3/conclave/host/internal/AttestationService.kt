package com.r3.conclave.host.internal

import com.r3.conclave.common.EnclaveMode
import com.r3.conclave.common.internal.ByteCursor
import com.r3.conclave.common.internal.EnclaveInstanceInfoImpl
import com.r3.conclave.common.internal.SgxSignedQuote
import com.r3.conclave.common.internal.attestation.AttestationResponse
import com.r3.conclave.common.internal.quote
import java.security.PublicKey

/**
 * An attestation service verifies the validity of enclave quotes.
 */
interface AttestationService {
    fun requestSignature(signedQuote: ByteCursor<SgxSignedQuote>): AttestationResponse

    fun doAttest(dataSigningKey: PublicKey, signedQuote: ByteCursor<SgxSignedQuote>, enclaveMode: EnclaveMode): EnclaveInstanceInfoImpl {
        val response = requestSignature(signedQuote)
        val enclaveInstanceInfo = EnclaveInstanceInfoImpl(dataSigningKey, response, enclaveMode)
        check(enclaveInstanceInfo.attestationReport.isvEnclaveQuoteBody == signedQuote.quote) {
            "The quote in the attestation report is not the one that was provided to the attestation service."
        }
        return enclaveInstanceInfo
    }
}
