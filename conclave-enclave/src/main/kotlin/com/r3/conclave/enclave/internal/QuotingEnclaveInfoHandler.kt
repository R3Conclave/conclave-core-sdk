package com.r3.conclave.enclave.internal

import com.r3.conclave.common.internal.ByteCursor
import com.r3.conclave.common.internal.Cursor
import com.r3.conclave.common.internal.SgxTargetInfo
import com.r3.conclave.common.internal.handler.Handler
import com.r3.conclave.common.internal.handler.Sender
import java.nio.ByteBuffer

/**
 * Handler for requesting quoting enclave info from the host.
 */
class QuotingEnclaveInfoHandler : Handler<QuotingEnclaveInfoHandler> {
    private lateinit var sender: Sender

    private val quotingEnclaveInfo = ThreadLocal<ByteCursor<SgxTargetInfo>>()

    override fun connect(upstream: Sender): QuotingEnclaveInfoHandler {
        sender = upstream
        return this
    }

    override fun onReceive(connection: QuotingEnclaveInfoHandler, input: ByteBuffer) {
        check(quotingEnclaveInfo.get() == null)
        quotingEnclaveInfo.set(Cursor.copy(SgxTargetInfo, input))
    }

    fun getQuotingEnclaveInfo(): ByteCursor<SgxTargetInfo> {
        try {
            sender.send(0) { /* No parameters for this request */ }
            return checkNotNull(quotingEnclaveInfo.get())
        } finally {
            quotingEnclaveInfo.remove()
        }
    }
}
