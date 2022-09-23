package com.r3.conclave.enclave.internal

import com.r3.conclave.common.MockCallInterfaceConnector
import com.r3.conclave.common.internal.EnclaveCallType
import com.r3.conclave.common.internal.HostCallType
import java.nio.ByteBuffer

class MockHostCallInterface(private val connector: MockCallInterfaceConnector) : HostCallInterface() {
    override fun initiateCall(callType: HostCallType, parameterBuffer: ByteBuffer): ByteBuffer {
        return connector.enclaveToHost(callType, parameterBuffer)
    }
}
