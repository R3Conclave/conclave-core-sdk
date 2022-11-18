package com.r3.conclave.host.internal.attestation

import com.r3.conclave.common.EnclaveMode
import com.r3.conclave.host.AttestationParameters
import com.r3.conclave.host.internal.EnclaveHandle
import com.r3.conclave.host.internal.NativeEnclaveHandle

object AttestationServiceFactory {
    /**
     * Return the correct attestation service for the given attestation parameters and enclave mode.
     *
     * EPID and DCAP can only be used if the enclave is release or debug, and mock is only used if the enclave is
     * simulation or mock. In the later case any attestation parameters provided are ignored.
     */
    fun getService(
        enclaveMode: EnclaveMode,
        attestationParameters: AttestationParameters?,
        enclaveHandle: EnclaveHandle
    ): AttestationService {
        return when (enclaveMode) {
            EnclaveMode.RELEASE -> getHardwareService(
                enclaveMode,
                attestationParameters,
                enclaveHandle
            )

            EnclaveMode.DEBUG -> getHardwareService(
                enclaveMode,
                attestationParameters,
                enclaveHandle
            )

            EnclaveMode.SIMULATION -> MockAttestationService(isSimulation = true)
            EnclaveMode.MOCK -> MockAttestationService(isSimulation = false)
        }
    }

    private fun getHardwareService(
        enclaveMode: EnclaveMode,
        attestationParameters: AttestationParameters?,
        enclaveHandle: EnclaveHandle
    ): HardwareAttestationService {
        return when (attestationParameters) {
            is AttestationParameters.EPID -> EpidAttestationService(
                enclaveMode == EnclaveMode.RELEASE,
                attestationParameters.attestationKey
            )

            is AttestationParameters.DCAP -> retrieveDCAPService(enclaveMode, enclaveHandle)
            null -> throw IllegalArgumentException("Attestation parameters needed for $enclaveMode mode.")
        }
    }

    private fun retrieveDCAPService(
        enclaveMode: EnclaveMode,
        enclaveHandle: EnclaveHandle
    ): HardwareAttestationService {
        return if (enclaveHandle is NativeEnclaveHandle) {
            DCAPAttestationService(enclaveMode == EnclaveMode.RELEASE)
        } else {
            DCAPAttestationService(enclaveMode == EnclaveMode.RELEASE)
        }
    }
}
