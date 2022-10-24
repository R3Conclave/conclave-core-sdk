package com.r3.conclave.host

import com.r3.conclave.common.OpaqueBytes

/**
 * Parameters that you may have to specify in order to obtain a particular kind of attestation for a loaded enclave.
 * This class abstracts the various vendor-specific schemes that exist.
 */
sealed class AttestationParameters {
    /**
     * Holds the "service provider ID" and "attestation key" values needed to use Intel Attestation Services (IAS).
     * You can get these parameters from the Intel website; please see the Conclave documentation for more details.
     *
     * Note that Intel does not provide EPID attestation support for Xeon scalable CPUs including Ice Lake and future 
     * generations. You need to use DCAP attestation on these platforms.
     *
     * @param spid The EPID Service Provider ID (or SPID) needed for creating the enclave quote for attesting. Please see
     * https://api.portal.trustedservices.intel.com/EPID-attestation for further details on how to obtain one. The EPID
     * signature mode must be Linkable Quotes.
     *
     * This parameter is not used if the enclave is in simulation mode (as no attestation is done in simulation) and null
     * can be provided.
     *
     * Note: This parameter is temporary and will be removed in a future version.
     *
     * @param attestationKey The private attestation key needed to access the attestation service. Please see
     * https://api.portal.trustedservices.intel.com/EPID-attestation for further details on how obtain one.
     *
     * This parameter is not used if the enclave is in simulation mode (as no attestation is done in simulation) and null
     * can be provided.
     */
    @Deprecated(
        "EPID attestation support is deprecated and it will be removed in an upcoming release," +
                " use DCAP attestation instead"
    )
    data class EPID(val spid: OpaqueBytes, val attestationKey: String) : AttestationParameters() {
        init {
            require(spid.size == 16) { "EPID service provider IDs are always 16 bytes" }
        }
    }

    /**
     * Indicates that Intel's newer DCAP attestation protocol is to be used. It is typically required that the
     * host system is pre-configured by a cloud provider to use DCAP. Azure Confidential VMs supports this mode
     * out of the box.
     */
    class DCAP : AttestationParameters()
}