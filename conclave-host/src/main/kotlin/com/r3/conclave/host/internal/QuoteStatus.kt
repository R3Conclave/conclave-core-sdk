package com.r3.conclave.host.internal

/**
 * Definition of each enum value is taken from https://api.trustedservices.intel.com/documents/sgx-attestation-api-spec.pdf
 * section 4.2.1.
 */
enum class QuoteStatus {
    /**
     * EPID signature of the ISV enclave QUOTE was verified correctly and the TCB level of the SGX platform is up-to-date.
     */
    OK,

    /**
     * EPID signature of the ISV enclave QUOTE was invalid. The content of the QUOTE is not trustworthy.
     */
    SIGNATURE_INVALID,

    /**
     * The EPID group has been revoked. When this value is returned, the revocationReason field of the Attestation
     * Verification Report will contain revocation reason code for this EPID group as reported in the EPID Group CRL.
     * The content of the QUOTE is not trustworthy.
     */
    GROUP_REVOKED,

    /**
     * The EPID private key used to sign the QUOTE has been revoked by signature. The content of the QUOTE is not trustworthy.
     */
    SIGNATURE_REVOKED,

    /**
     * The EPID private key used to sign the QUOTE has been directly revoked (not by signature). The content of the QUOTE
     * is not trustworthy.
     */
    KEY_REVOKED,

    /**
     * SigRL version in ISV enclave QUOTE does not match the most recent version of the SigRL. In rare situations,
     * after SP retrieved the SigRL from IAS and provided it to the platform, a newer version of the SigRL is made
     * available. As a result, the Attestation Verification Report will indicate SIGRL_VERSION_MISMATCH. SP can retrieve
     * the most recent version of SigRL from the IAS and request the platform to perform remote attestation again with
     * the most recent version of SigRL. If the platform keeps failing to provide a valid QUOTE matching with the most
     * recent version of the SigRL, the content of the QUOTE is not trustworthy.
     */
    SIGRL_VERSION_MISMATCH,

    /**
     * The EPID signature of the ISV enclave QUOTE has been verified correctly, but the TCB level of SGX platform is
     * outdated (for further details see Advisory IDs). The platform has not been identified as compromised and thus it
     * is not revoked. It is up to the Service Provider to decide whether or not to trust the content of the QUOTE, and
     * whether or not to trust the platform performing the attestation to protect specific sensitive information.
     */
    GROUP_OUT_OF_DATE,

    /**
     * The EPID signature of the ISV enclave QUOTE has been verified correctly, but additional configuration of SGX
     * platform may be needed (for further details see Advisory IDs). The platform has not been identified as compromised
     * and thus it is not revoked. It is up to the Service Provider to decide whether or not to trust the content of the
     * QUOTE, and whether or not to trust the platform performing the attestation to protect specific sensitive
     * information.
     */
    CONFIGURATION_NEEDED
}
