package com.r3.conclave.host.internal

import com.r3.conclave.common.internal.CallInterface
import com.r3.conclave.common.internal.MockCallInterfaceConnector
import com.r3.conclave.common.internal.EnclaveCallType
import com.r3.conclave.common.internal.HostCallType
import java.nio.ByteBuffer

/**
 * This class is the implementation of the [HostEnclaveInterface] for mock enclaves.
 * It has three jobs:
 *  - Serve as the endpoint for calls to make to the enclave, see [com.r3.conclave.common.internal.CallInterface]
 *  - Route calls from the enclave to the appropriate host side call handler, see [com.r3.conclave.common.internal.CallInterface]
 *  - Handle the low-level details of the messaging protocol (in this case, not much!), see [MockCallInterfaceConnector].
 */
class MockHostEnclaveInterface(private val connector: MockCallInterfaceConnector) : CallInterface<EnclaveCallType, HostCallType>() {
    init {
        connector.setEnclaveHostInterface(this)
    }

    override fun executeOutgoingCall(callType: EnclaveCallType, parameterBuffer: ByteBuffer): ByteBuffer? {
        return connector.hostToEnclave(callType, parameterBuffer)
    }
}
