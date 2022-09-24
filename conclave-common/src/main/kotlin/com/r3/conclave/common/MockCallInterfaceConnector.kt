package com.r3.conclave.common

import com.r3.conclave.common.internal.CallAcceptor
import com.r3.conclave.common.internal.EnclaveCallType
import com.r3.conclave.common.internal.HostCallType
import com.r3.conclave.common.internal.ThreadLocalEnclaveContext
import com.r3.conclave.utilities.internal.EnclaveContext
import com.r3.conclave.utilities.internal.getAllBytes
import com.r3.conclave.utilities.internal.getRemainingBytes
import java.nio.ByteBuffer

/**
 * This class serves as the glue between the host and a mock enclave.
 * An object of this class exists "between" the mock enclave and the enclave host and servers
 * to connect the MockHostCallInterface to the MockEnclaveCallInterface.
 * Parameter and return value buffers are deep copied to better emulate the behaviour of a real enclave.
 */
class MockCallInterfaceConnector {
    companion object {
        private fun copyBuffer(buffer: ByteBuffer): ByteBuffer {
            return ByteBuffer.wrap(buffer.getAllBytes())
        }
    }

    private lateinit var enclaveCallAcceptor: CallAcceptor<EnclaveCallType>
    private lateinit var hostCallAcceptor: CallAcceptor<HostCallType>

    fun setHostCallAcceptor(acceptor: CallAcceptor<HostCallType>) {
        hostCallAcceptor = acceptor
    }

    fun setEnclaveCallAcceptor(acceptor: CallAcceptor<EnclaveCallType>) {
        enclaveCallAcceptor = acceptor
    }

    fun enclaveToHost(callType: HostCallType, parameterBuffer: ByteBuffer): ByteBuffer? {
        check(EnclaveContext.isInsideEnclave())
        ThreadLocalEnclaveContext.set(false)
        try {
            val parameterBufferCopy = copyBuffer(parameterBuffer)
            hostCallAcceptor.acceptCall(callType, parameterBufferCopy)?.let { return copyBuffer(it) }
            return null
        } finally {
            ThreadLocalEnclaveContext.set(true)
        }
    }

    fun hostToEnclave(callType: EnclaveCallType, parameterBuffer: ByteBuffer): ByteBuffer? {
        check(!EnclaveContext.isInsideEnclave())
        ThreadLocalEnclaveContext.set(true)
        try {
            val parameterBufferCopy = copyBuffer(parameterBuffer)
            enclaveCallAcceptor.acceptCall(callType, parameterBufferCopy)?.let { return copyBuffer(it) }
            return null
        } finally {
            ThreadLocalEnclaveContext.set(false)
        }
    }
}
