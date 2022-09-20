package com.r3.conclave.host.internal

import com.r3.conclave.common.internal.*
import com.r3.conclave.utilities.internal.getRemainingBytes
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets

abstract class EnclaveCallInterface : CallInitiator<EnclaveCallType>, CallAcceptor<HostCallType>() {
    fun initializeEnclave(enclaveClassName: String) {
        initiateCall(EnclaveCallType.INITIALIZE_ENCLAVE, ByteBuffer.wrap(enclaveClassName.toByteArray(StandardCharsets.UTF_8)))
    }

    fun getEnclaveInstanceInfoQuote(target: ByteCursor<SgxTargetInfo>): ByteCursor<SgxSignedQuote> {
        val returnBuffer = initiateCall(EnclaveCallType.GET_ENCLAVE_INSTANCE_INFO_QUOTE, target.buffer)!!
        return Cursor.wrap(SgxSignedQuote, returnBuffer.getRemainingBytes())
    }
}
