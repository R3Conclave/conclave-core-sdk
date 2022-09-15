package com.r3.conclave.host.internal

import com.r3.conclave.common.internal.CallAcceptor
import com.r3.conclave.common.internal.CallInitiator
import com.r3.conclave.common.internal.EnclaveCallType
import com.r3.conclave.common.internal.HostCallType
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets

abstract class EnclaveCallInterface : CallInitiator<EnclaveCallType>, CallAcceptor<HostCallType>() {
    fun initializeEnclave(enclaveClassName: String) {
        initiateCall(EnclaveCallType.INITIALIZE_ENCLAVE, ByteBuffer.wrap(enclaveClassName.toByteArray(StandardCharsets.UTF_8)))
    }
}
