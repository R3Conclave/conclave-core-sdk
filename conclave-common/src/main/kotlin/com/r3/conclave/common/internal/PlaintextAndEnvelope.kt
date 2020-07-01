package com.r3.conclave.common.internal

import com.r3.conclave.common.OpaqueBytes

/**
 * Unsealed data holder, used by the sealing conclave API.
 * @param plaintext unsealed text.
 * @param authenticatedData optional authenticated data.
 */
data class PlaintextAndEnvelope(val plaintext: OpaqueBytes, val authenticatedData: OpaqueBytes? = null)