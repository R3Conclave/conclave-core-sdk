package com.r3.conclave.integrationtests.kds

import com.r3.conclave.common.OpaqueBytes
import com.r3.conclave.host.AttestationParameters
import com.r3.conclave.host.EnclaveHost
import com.r3.conclave.host.MailCommand
import com.r3.conclave.host.kds.KDSConfiguration
import java.io.File
import java.nio.file.Files
import java.nio.file.Paths

// TODO This needs to be replaced with a proper integration test
class PersistentHost {
    companion object {
        private val kdsPort = System.getProperty("kdsPort").toInt()

        @JvmStatic
        fun main(args: Array<String>) {
            val kdsConfiguration = KDSConfiguration("http://localhost:$kdsPort")
            EnclaveHost.load("com.r3.conclave.integrationtests.kds.PersistentEnclave", null).use { enclave ->
                val sealedStateFile = File("sealed-state")
                val sealedState: ByteArray? = if (sealedStateFile.isFile) {
                    sealedStateFile.readBytes()
                } else {
                    null
                }
                enclave.start(initializeAttestationParameters(), sealedState, Paths.get("filesystem"), kdsConfiguration) { mailCommands ->
                    for (mailCommand in mailCommands) {
                        if (mailCommand is MailCommand.StoreSealedState) {
                            Files.write(Paths.get("sealed-state"), mailCommand.sealedState)
                        }
                    }
                }
                enclave.callEnclave("value".toByteArray())
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