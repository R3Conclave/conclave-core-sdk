package com.r3.conclave.common

import com.r3.conclave.common.internal.CallInterface
import com.r3.conclave.common.internal.EnclaveCallType
import com.r3.conclave.common.internal.HostCallType
import com.r3.conclave.common.internal.ThreadLocalEnclaveContext
import com.r3.conclave.utilities.internal.EnclaveContext
import com.r3.conclave.utilities.internal.getAllBytes
import java.nio.ByteBuffer

/**
 * This class serves as the glue between a mock host and a mock enclave.
 * An instance of this class exists "between" the mock enclave and the host, bridging the
 * [com.r3.conclave.enclave.internal.HostCallInterface] to the [com.r3.conclave.host.internal.EnclaveCallInterface].
 * Parameter and return value buffers are deep copied to more accurately emulate the behaviour of an actual enclave.
 */
class MockCallInterfaceConnector {
    companion object {
        private fun copyBuffer(buffer: ByteBuffer): ByteBuffer {
            return ByteBuffer.wrap(buffer.getAllBytes(avoidCopying = false))
        }
    }

    private lateinit var enclaveCallAcceptor: CallInterface<HostCallType, EnclaveCallType>
    private lateinit var hostCallAcceptor: CallInterface<EnclaveCallType, HostCallType>

    fun setHostCallAcceptor(acceptor: CallInterface<EnclaveCallType, HostCallType>) {
        hostCallAcceptor = acceptor
    }

    fun setEnclaveCallAcceptor(acceptor: CallInterface<HostCallType, EnclaveCallType>) {
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
