package com.r3.conclave.host.internal

import com.r3.conclave.common.internal.CallAcceptor
import com.r3.conclave.common.internal.EnclaveCallType
import java.lang.IllegalArgumentException
import java.nio.ByteBuffer

class MockEnclaveCallInterface(private val enclaveCallAcceptor: CallAcceptor<EnclaveCallType>) : EnclaveCallInterface() {
    override fun initiateCall(callType: EnclaveCallType, parameterBuffer: ByteBuffer?): ByteBuffer? {
        return enclaveCallAcceptor.acceptCall(callType, parameterBuffer)
    }
}
