package com.r3.conclave.host.internal.attestation

import com.r3.conclave.common.internal.*
import com.r3.conclave.host.internal.Native
import com.r3.conclave.host.internal.NativeLoader
import java.nio.ByteBuffer

class EnclaveQuoteServiceGramineDCAP : EnclaveQuoteService() {

    override fun initializeQuote(): Cursor<SgxTargetInfo, ByteBuffer> {
        val targetInfo = Cursor.allocate(SgxTargetInfo)
        //  When using Gramine, we do not need to load (dlsym) all the quoting libraries,
        //    hence we pass a true bool value to avoid loading them.
        Native.initQuoteDCAP(NativeLoader.libsPath.toString(), true, targetInfo.buffer.array())
        return targetInfo
    }

    override fun retrieveQuote(report: ByteCursor<SgxReport>): ByteCursor<SgxSignedQuote> {
        //  TODO: fix the abstraction here, so that this call is only used in the context of Native Image
        //  This is not executed in the context of Gramine flow.
        throw IllegalStateException("The quote can't be retrieved from the host when running Gramine")
    }
}
