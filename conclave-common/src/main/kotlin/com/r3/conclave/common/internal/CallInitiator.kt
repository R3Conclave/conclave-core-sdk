package com.r3.conclave.common.internal

import java.nio.ByteBuffer

interface CallInitiator<CALL_ID_TYPE> {
    companion object {
        private val EMPTY_BYTE_BUFFER: ByteBuffer = ByteBuffer.wrap(ByteArray(0)).asReadOnlyBuffer()
    }



}
