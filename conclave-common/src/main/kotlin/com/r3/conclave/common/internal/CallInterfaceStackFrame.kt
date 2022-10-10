package com.r3.conclave.common.internal

import java.nio.ByteBuffer

/**
 * This is used in the call interface to relate return or exception messages back to the call message which
 * they are intended for. See [com.r3.conclave.enclave.internal.NativeEnclaveHostInterface] and
 * [com.r3.conclave.host.internal.NativeHostEnclaveInterface].
 */
class CallInterfaceStackFrame<CALL_TYPE>(
        val callType: CALL_TYPE,
        var returnBuffer: ByteBuffer? = null,
        var exceptionBuffer: ByteBuffer? = null
)
