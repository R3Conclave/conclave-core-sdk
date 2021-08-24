package com.r3.conclave.host.internal.attestation

import com.r3.conclave.common.EnclaveMode
import com.r3.conclave.common.internal.ByteCursor
import com.r3.conclave.common.internal.SgxSignedQuote
import com.r3.conclave.common.internal.attestation.Attestation
import com.r3.conclave.common.internal.attestation.HardwareAttestation

/**
 * A service which asserts the validity of an enclave by attesting to its [SgxSignedQuote]. The result is a [Attestation]
 * object which encapsulutes the attestation result.
 */
interface AttestationService {
    fun attestQuote(signedQuote: ByteCursor<SgxSignedQuote>): Attestation
}

/**
 * An attestation service for enclaves running on real hardware. The attestation objects are sub-types of [HardwareAttestation].
 *
 * @property isRelease Since the [HardwareAttestation.enclaveMode] property is dependent on the enclave's debug flag,
 * this is used to make sure the flag value matches what the attestation service is expecting.
 */
abstract class HardwareAttestationService : AttestationService {
    abstract val isRelease: Boolean

    final override fun attestQuote(signedQuote: ByteCursor<SgxSignedQuote>): HardwareAttestation {
        val attestation = doAttestQuote(signedQuote)
        val expectedMode = if (isRelease) EnclaveMode.RELEASE else EnclaveMode.DEBUG
        check(attestation.enclaveMode == expectedMode) {
            "Mismatch in the enclave mode. Expected $expectedMode but got ${attestation.enclaveMode} from the attestation."
        }
        return attestation
    }

    abstract fun doAttestQuote(signedQuote: ByteCursor<SgxSignedQuote>): HardwareAttestation
}
