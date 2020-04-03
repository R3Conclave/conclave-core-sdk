package com.r3.conclave.common.internal.attestation

/**
 * Definition of each enum value is taken from https://api.trustedservices.intel.com/documents/sgx-attestation-api-spec.pdf
 * section 4.2.1.
 */
enum class ManifestStatus {
    /**
     * Security properties of the SGX Platform Service was verified as valid and up-to-date.
     */
    OK,

    /**
     * Security properties of the SGX Platform Service cannot be verified due to unrecognized PSE Manifest.
     */
    UNKNOWN,

    /**
     * Security properties of the SGX Platform Service are invalid. SP should assume the SGX Platform Service utilized
     * by the ISV enclave is invalid.
     */
    INVALID,

    /**
     * TCB level of SGX Platform Service is outdated but the Service has not been identified as compromised and thus it
     * is not revoked. It is up to the SP to decide whether or not to assume the SGX Platform Service utilized by the ISV
     * enclave is valid.
     */
    OUT_OF_DATE,

    /**
     * The hardware/firmware component involved in the SGX Platform Service has been revoked. SP should assume the SGX
     * Platform Service utilized by the ISV enclave is invalid.
     */
    REVOKED,

    /**
     * A specific type of Revocation List used to verify the hardware/firmware component involved in the SGX Platform
     * Service during the SGX Platform Service initialization process is out of date. If the SP rejects the remote
     * attestation and forwards the Platform Info Blob to the SGX Platform SW through the ISV SGX Application, the SGX
     * Platform SW will attempt to refresh the SGX Platform Service.
     */
    RL_VERSION_MISMATCH
}
