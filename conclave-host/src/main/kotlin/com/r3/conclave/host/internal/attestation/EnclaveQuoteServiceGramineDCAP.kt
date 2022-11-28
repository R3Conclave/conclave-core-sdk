package com.r3.conclave.host.internal.attestation

import com.r3.conclave.common.internal.*
import com.r3.conclave.host.internal.Native
import com.r3.conclave.host.internal.NativeLoader
import java.nio.ByteBuffer

//  TODO: fix the abstraction related to the usage of this class and then remove it, as it doesn't do much.
object EnclaveQuoteServiceGramineDCAP : EnclaveQuoteService() {

    // Gramine does not need to use this target info as this is automatically retrieved when signing the quote
    private val targetInfo = Cursor.allocate(SgxTargetInfo).also {
        //  When using Gramine, we do not need to load (dlsym) all the quoting libraries,
        //    hence we pass a false bool value to avoid loading them.
        //  TODO: Remove the unnecessary passing of the byte array once we have improved Native.initQuoteDCAP
        Native.initQuoteDCAP(NativeLoader.libsPath.toString(), false, it.buffer.array())
    }

    override fun getQuotingEnclaveInfo(): Cursor<SgxTargetInfo, ByteBuffer> {
        return targetInfo
    }

    override fun retrieveQuote(report: ByteCursor<SgxReport>): ByteCursor<SgxSignedQuote> {
        //  This is not executed in the context of Gramine flow.
        throw IllegalStateException("The quote can't be retrieved from the host when running Gramine")
    }
}
