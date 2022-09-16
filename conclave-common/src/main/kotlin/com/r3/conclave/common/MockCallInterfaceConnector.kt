package com.r3.conclave.common

import com.r3.conclave.common.internal.CallAcceptor
import com.r3.conclave.common.internal.EnclaveCallType
import com.r3.conclave.common.internal.HostCallType
import java.nio.ByteBuffer

/**
 * This class serves as the glue between the host and a mock enclave.
 * An object of this class exists "between" the mock enclave and the enclave host and servers
 * to connect the MockHostCallInterface to the MockEnclaveCallInterface.
 */
class MockCallInterfaceConnector {
    private lateinit var enclaveCallAcceptor: CallAcceptor<EnclaveCallType>
    private lateinit var hostCallAcceptor: CallAcceptor<HostCallType>

    fun setHostCallAcceptor(acceptor: CallAcceptor<HostCallType>) {
        hostCallAcceptor = acceptor
    }

    fun setEnclaveCallAcceptor(acceptor: CallAcceptor<EnclaveCallType>) {
        enclaveCallAcceptor = acceptor
    }

    fun enclaveToHost(callType: HostCallType, parameterBuffer: ByteBuffer?): ByteBuffer? {
        return hostCallAcceptor.acceptCall(callType, parameterBuffer)
    }

    fun hostToEnclave(callType: EnclaveCallType, parameterBuffer: ByteBuffer?): ByteBuffer? {
        return enclaveCallAcceptor.acceptCall(callType, parameterBuffer)
    }
}
