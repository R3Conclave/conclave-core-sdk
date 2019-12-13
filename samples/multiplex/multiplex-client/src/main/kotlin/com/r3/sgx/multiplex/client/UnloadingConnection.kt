package com.r3.sgx.multiplex.client

interface UnloadingConnection {
    /**
     * @param enclaveConnection An [EnclaveConnection] object for a dynamic enclave.
     */
    fun unload(enclaveConnection: EnclaveConnection)
}
