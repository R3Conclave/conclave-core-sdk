package com.r3.conclave.host

import com.r3.conclave.common.OpaqueBytes

/**
 * Parameters that you may have to specify in order to obtain a particular kind of attestation for a loaded enclave.
 * This class abstracts the various vendor-specific schemes that exist.
 */
sealed class AttestationParameters {

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
