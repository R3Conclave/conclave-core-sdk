package com.r3.conclave.enclave.internal

import com.r3.conclave.common.internal.*
import com.r3.conclave.common.internal.handler.Handler
import com.r3.conclave.common.internal.handler.Sender
import java.nio.ByteBuffer

/**
 * Handler for requesting a signed quote from the host.
 */
class SignedQuoteHandler : Handler<SignedQuoteHandler> {
    private lateinit var sender: Sender

    private val signedQuote = ThreadLocal<ByteCursor<SgxSignedQuote>>()

    override fun connect(upstream: Sender): SignedQuoteHandler {
        sender = upstream
        return this
    }

    override fun onReceive(connection: SignedQuoteHandler, input: ByteBuffer) {
        check(signedQuote.get() == null)
        signedQuote.set(Cursor.copy(SgxSignedQuote, input))
    }

    fun getSignedQuote(report: ByteCursor<SgxReport>): ByteCursor<SgxSignedQuote> {
        try {
            sender.send(report.size) { buffer ->
                buffer.put(report.buffer)
            }
            return checkNotNull(signedQuote.get())
        } finally {
            signedQuote.remove()
        }
    }
}
