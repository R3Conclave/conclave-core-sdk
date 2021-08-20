package com.r3.conclave.host.internal

import com.r3.conclave.common.internal.ByteCursor
import com.r3.conclave.common.internal.SgxQuote.reportBody
import com.r3.conclave.common.internal.SgxSignedQuote
import com.r3.conclave.common.internal.SgxSignedQuote.quote
import com.r3.conclave.common.internal.attestation.MockAttestation
import com.r3.conclave.host.internal.AttestationService
import java.time.Instant

/**
 * A mock attestation service which provides fake attestations which always marks the enclave quote as insecure.
 *
 * @param isSimulation Whether the [MockAttestation.enclaveMode] property of the attestation is simulation or mock.
 */
class AttestationServiceMock(private val isSimulation: Boolean) : AttestationService {
    override fun attestQuote(signedQuote: ByteCursor<SgxSignedQuote>): MockAttestation {
        return MockAttestation(Instant.now(), signedQuote[quote][reportBody].asReadOnly(), isSimulation)
    }
}
