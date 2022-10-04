package com.r3.conclave.host.internal

import com.r3.conclave.common.MockCallInterfaceConnector
import com.r3.conclave.common.internal.EnclaveCallType
import java.nio.ByteBuffer

/**
 * This class is the implementation of the [EnclaveCallInterface] for mock enclaves.
 * It has three jobs:
 *  - Serve as the endpoint for calls to make to the enclave, see [com.r3.conclave.common.internal.CallInitiator]
 *  - Route calls from the enclave to the appropriate host side call handler, see [com.r3.conclave.common.internal.CallInterface]
 *  - Handle the low-level details of the messaging protocol (in this case, not much!), see [MockCallInterfaceConnector].
 */
class MockEnclaveCallInterface(private val connector: MockCallInterfaceConnector) : EnclaveCallInterface() {
    override fun executeCall(callType: EnclaveCallType, parameterBuffer: ByteBuffer): ByteBuffer? {
        return connector.hostToEnclave(callType, parameterBuffer)
    }
}
