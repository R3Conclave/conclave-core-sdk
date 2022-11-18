package com.r3.conclave.host.internal.attestation

import com.r3.conclave.common.internal.*
import com.r3.conclave.host.internal.Native
import com.r3.conclave.host.internal.NativeLoader
import java.nio.ByteBuffer

// Note: we actually do not need this class in Gramine
// TODO: Avoid calling initializeQuote in Gramine and remove this class
class EnclaveQuoteServiceGramineDCAP : EnclaveQuoteService() {

    override fun initializeQuote(): Cursor<SgxTargetInfo, ByteBuffer> {
        val targetInfo = Cursor.allocate(SgxTargetInfo)
        Native.initQuoteDCAP(NativeLoader.libsPath.toString(), true, targetInfo.buffer.array())
        return targetInfo
    }

    override fun retrieveQuote(report: ByteCursor<SgxReport>): ByteCursor<SgxSignedQuote> {
        // This is actually not called in GRAMINE
        return Cursor.wrap(SgxSignedQuote, byteArrayOf())
    }
}
