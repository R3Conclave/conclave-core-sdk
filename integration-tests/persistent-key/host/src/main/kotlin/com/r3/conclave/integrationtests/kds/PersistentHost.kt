package com.r3.conclave.integrationtests.kds

import com.r3.conclave.common.OpaqueBytes
import com.r3.conclave.host.AttestationParameters
import com.r3.conclave.host.EnclaveHost
import com.r3.conclave.host.kds.KDSConfiguration
import java.nio.file.Paths

// TODO This needs to be replaced with a proper integration test
class PersistentHost {
    companion object {
        private val kdsPort = System.getProperty("kdsPort").toInt()

        @JvmStatic
        fun main(args: Array<String>) {
            val kdsConfiguration = KDSConfiguration("http://localhost:$kdsPort")
            EnclaveHost.load("com.r3.conclave.integrationtests.kds.PersistentEnclave", null).use { enclave ->
                enclave.start(initializeAttestationParameters(), null, Paths.get("filesystem"), kdsConfiguration) {
                }
            }
        }

        private fun initializeAttestationParameters(): AttestationParameters {
            if ("epid" == System.getProperty("attestationMode")) {
                val spid = OpaqueBytes.parse(System.getProperty("conclave.spid"))
                val attestationKey = System.getProperty("conclave.attestation-key")
                return AttestationParameters.EPID(spid, attestationKey)
            }
            return AttestationParameters.DCAP()
        }
    }
}