package com.r3.conclave.integrationtests.general.enclave

import com.r3.conclave.common.internal.Cursor
import com.r3.conclave.common.internal.SgxKeyRequest
import com.r3.conclave.enclave.Enclave
import com.r3.conclave.enclave.internal.EnclaveEnvironment

class SecretKeyEnclave2 : Enclave() {

    private val env : EnclaveEnvironment by lazy {
        Enclave::class.java.getDeclaredField("env").apply { isAccessible = true }.get(this) as EnclaveEnvironment
    }

    override fun receiveFromUntrustedHost(bytes: ByteArray): ByteArray {

        return env.getSecretKey(Cursor.wrap(SgxKeyRequest.INSTANCE, bytes, 0, bytes.size))
    }
}

