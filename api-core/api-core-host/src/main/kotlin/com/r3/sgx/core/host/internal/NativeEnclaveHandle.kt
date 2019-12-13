package com.r3.sgx.core.host.internal

import com.r3.sgx.core.host.EnclaveHandle
import com.r3.sgx.core.host.NativeHostApi

class NativeEnclaveHandle<CONNECTION>(
        private val hostApi: NativeHostApi,
        private val enclaveId: EnclaveId,
        override val connection: CONNECTION
) : EnclaveHandle<CONNECTION> {
    override fun destroy() {
        hostApi.destroyEnclave(enclaveId)
    }
}
