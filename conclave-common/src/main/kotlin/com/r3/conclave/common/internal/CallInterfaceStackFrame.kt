package com.r3.conclave.common.internal

import java.nio.ByteBuffer

class CallInterfaceStackFrame<CALL_TYPE>(
        val callType: CALL_TYPE,
        var returnBuffer: ByteBuffer? = null,
        var exceptionBuffer: ByteBuffer? = null
)
