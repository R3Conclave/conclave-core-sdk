package com.r3.conclave.host.internal

import com.r3.conclave.common.internal.EnclaveCallType
import com.r3.conclave.common.internal.MockCallInterfaceConnector
import java.nio.ByteBuffer

/**
 * This class is the implementation of the [HostEnclaveInterface] for Gramine enclaves.
 * It is currently in an experimental state. Do not use it.
 * //  TODO: Refactor this class to move it to a non experimental state
 *  - Serve as the endpoint for calls to make to the enclave, see [com.r3.conclave.common.internal.CallInterface]
 *  - Route calls from the enclave to the appropriate host side call handler, see [com.r3.conclave.common.internal.CallInterface]
 *  - Handle the low-level details of the messaging protocol (ECalls and OCalls).
 */
class GramineHostEnclaveInterface(private val connector: MockCallInterfaceConnector) : HostEnclaveInterface() {
    init {
        connector.setEnclaveHostInterface(this)
    }

    override fun executeOutgoingCall(callType: EnclaveCallType, parameterBuffer: ByteBuffer): ByteBuffer? {
        return null
    }
}
