package com.r3.conclave.host.internal.attestation

import com.r3.conclave.common.internal.*
import java.nio.ByteBuffer

abstract class EnclaveQuoteService {
    abstract fun getQuotingEnclaveInfo(): Cursor<SgxTargetInfo, ByteBuffer>
    abstract fun retrieveQuote(report: ByteCursor<SgxReport>): ByteCursor<SgxSignedQuote>
}
