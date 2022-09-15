package com.r3.conclave.common.internal

import java.nio.ByteBuffer

interface CallInitiator<CALL_ID_TYPE> {
    fun initiateCall(callType: CALL_ID_TYPE, parameterBuffer: ByteBuffer?): ByteBuffer?
}
