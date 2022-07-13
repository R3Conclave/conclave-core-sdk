package com.r3.conclave.integrationtests.general.commontest

import com.r3.conclave.common.OpaqueBytes
import com.r3.conclave.host.AttestationParameters
import com.r3.conclave.host.EnclaveHost
import java.nio.file.Path
import kotlin.io.path.exists

object TestUtils {
    fun getAttestationParams(enclaveHost: EnclaveHost): AttestationParameters? {
        return if (enclaveHost.enclaveMode.isHardware) getHardwareAttestationParams() else null
    }

    private fun getHardwareAttestationParams(): AttestationParameters {
        return when {
            // EPID vs DCAP can be detected because the drivers are different and have different names.
            Path.of("/dev/sgx_enclave").exists() || Path.of("/dev/sgx/enclave").exists() -> {
                AttestationParameters.DCAP()
            }
            Path.of("/dev/isgx").exists() -> {
                val spid = OpaqueBytes.parse(System.getProperty("conclave.spid"))
                val attestationKey = checkNotNull(System.getProperty("conclave.attestation-key"))
                AttestationParameters.EPID(spid, attestationKey)
            }
            else -> throw UnsupportedOperationException(
                "SGX does not appear to be available on this machine. Check kernel drivers."
            )
        }
    }
}
