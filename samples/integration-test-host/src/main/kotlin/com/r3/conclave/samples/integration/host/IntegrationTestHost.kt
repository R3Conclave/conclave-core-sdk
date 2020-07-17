package com.r3.conclave.samples.integration.host

import com.r3.conclave.common.EnclaveInstanceInfo
import com.r3.conclave.common.OpaqueBytes
import com.r3.conclave.host.EnclaveHost

class IntegrationTestHost : AutoCloseable {
    private val enclaveHost = EnclaveHost.load("com.r3.conclave.samples.integration.enclave.IntegrationTestEnclave")

    fun start(spid: OpaqueBytes?, attestationKey: String?) {
        enclaveHost.start(spid, attestationKey, null)
    }

    val enclaveInstanceInfo: EnclaveInstanceInfo get() = enclaveHost.enclaveInstanceInfo

    override fun close() {
        enclaveHost.close()
    }
}
