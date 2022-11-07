package com.r3.conclave.host.internal.attestation

import com.r3.conclave.common.EnclaveMode
import com.r3.conclave.host.AttestationParameters
import com.r3.conclave.host.internal.EnclaveHandle
import com.r3.conclave.host.internal.gramine.GramineDirectEnclaveHandle
import com.r3.conclave.host.internal.gramine.GramineSGXEnclaveHandle

object AttestationServiceFactory {
    enum class AttestationCreationMode{
        NORMAL,
        FORCE_MOCK_ATTESTATION
    }
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
        return if (enclaveHandle is GramineDirectEnclaveHandle || enclaveHandle is GramineSGXEnclaveHandle) MockAttestationService(isSimulation = true)
        else
            when (enclaveMode) {
                EnclaveMode.RELEASE -> getHardwareService(isRelease = true, enclaveMode, attestationParameters)
                EnclaveMode.DEBUG -> getHardwareService(isRelease = false, enclaveMode, attestationParameters)
                EnclaveMode.SIMULATION -> MockAttestationService(isSimulation = true)
                EnclaveMode.MOCK -> MockAttestationService(isSimulation = false)
            }
    }

    private fun getHardwareService(isRelease: Boolean, enclaveMode: EnclaveMode, attestationParameters: AttestationParameters?): HardwareAttestationService {
        return when (attestationParameters) {
            is AttestationParameters.EPID -> EpidAttestationService(isRelease, attestationParameters.attestationKey)
            is AttestationParameters.DCAP -> DCAPAttestationService(isRelease)
            null -> throw IllegalArgumentException("Attestation parameters needed for $enclaveMode mode.")
        }
    }
}