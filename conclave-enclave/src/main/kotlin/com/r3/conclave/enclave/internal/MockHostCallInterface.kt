package com.r3.conclave.enclave.internal

import com.r3.conclave.common.MockCallInterfaceConnector
import com.r3.conclave.common.internal.HostCallType
import java.nio.ByteBuffer

/**
 * This class is the implementation of the [HostCallInterface] for mock enclaves.
 * It has three jobs:
 *  - Serve as the endpoint for calls to make to the host, see [com.r3.conclave.common.internal.CallInitiator]
 *  - Route calls from the host to the appropriate enclave side call handler, see [com.r3.conclave.common.internal.CallInterface]
 *  - Handle the low-level details of the messaging protocol (in this case, not much!), see [MockCallInterfaceConnector].
 */
class MockHostCallInterface(private val connector: MockCallInterfaceConnector) : HostCallInterface() {
    override fun initiateOutgoingCall(callType: HostCallType, parameterBuffer: ByteBuffer): ByteBuffer? {
        return connector.enclaveToHost(callType, parameterBuffer)
    }
}
