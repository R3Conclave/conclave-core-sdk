package com.r3.conclave.enclave.internal

import com.r3.conclave.common.internal.CallAcceptor
import com.r3.conclave.common.internal.HostCallType
import java.nio.ByteBuffer

class MockHostCallInterface(private val hostCallAcceptor: CallAcceptor<HostCallType>) : HostCallInterface() {
    override fun initiateCall(callType: HostCallType, parameterBuffer: ByteBuffer?): ByteBuffer? {
        return hostCallAcceptor.acceptCall(callType, parameterBuffer)
    }
}
