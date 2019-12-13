package com.r3.sgx.multiplex.client

import com.r3.sgx.core.common.Handler
import com.r3.sgx.core.common.MuxId
import java.nio.ByteBuffer

interface EnclaveConnection {
    val enclaveHash: ByteBuffer
    val id: MuxId

    /**
     * @param downstream The [Handler] to be installed at the root of this enclave's handler tree.
     */
    fun <CONNECTION> setDownstream(downstream: Handler<CONNECTION>): CONNECTION
}