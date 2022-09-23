package com.r3.conclave.host.internal

import com.r3.conclave.common.MockCallInterfaceConnector
import com.r3.conclave.common.internal.EnclaveCallType
import com.r3.conclave.common.internal.HostCallType
import java.nio.ByteBuffer

class MockEnclaveCallInterface(private val connector: MockCallInterfaceConnector) : EnclaveCallInterface() {
    override fun initiateCall(callType: EnclaveCallType, parameterBuffer: ByteBuffer): ByteBuffer {
        return connector.hostToEnclave(callType, parameterBuffer)
    }

    override fun acceptCall(callType: HostCallType, parameterBuffer: ByteBuffer): ByteBuffer {
        return getCallHandler(callType).handleCall(parameterBuffer)
    }
}
