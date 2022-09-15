package com.r3.conclave.host.internal

import com.r3.conclave.common.internal.EnclaveCallType
import java.lang.IllegalArgumentException
import java.nio.ByteBuffer

class MockEnclaveCallInterface : EnclaveCallInterface() {
    override fun initiateCall(callType: EnclaveCallType, parameters: ByteBuffer?): ByteBuffer? {
        // TODO
        throw IllegalArgumentException("Mock enclave call initiator not yet implemented!")
    }
}
