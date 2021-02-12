package com.r3.conclave.internaltesting

import com.r3.conclave.common.OpaqueBytes
import com.r3.conclave.host.AttestationParameters
import org.junit.jupiter.api.Tag
import java.nio.file.Files
import java.nio.file.Paths

/**
 * Implement this interface if the unit test needs to run on real SGX hardware.
 */
@Tag("hardware")
interface HardwareTest {
    companion object {
        val attestationParams: AttestationParameters by lazy {
            when {
                // EPID vs DCAP can be detected because the drivers are different and have different names.
                Files.exists(Paths.get("/dev/isgx")) -> {
                    println("Using EPID...")
                    val spid = OpaqueBytes.parse(System.getProperty("conclave.spid"))
                    val attestationKey = checkNotNull(System.getProperty("conclave.attestation-key"))
                    AttestationParameters.EPID(spid, attestationKey)
                }
                Files.exists(Paths.get("/dev/sgx/enclave")) -> {
                    println("Using DCAP...")
                    AttestationParameters.DCAP()
                }
                else -> throw UnsupportedOperationException(
                    "SGX does not appear to be available on this machine. Check kernel drivers."
                )
            }
        }
    }
}
