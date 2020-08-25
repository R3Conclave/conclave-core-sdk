package com.r3.conclave.internaltesting

import com.r3.conclave.common.OpaqueBytes
import org.junit.jupiter.api.Tag

/**
 * Extend this class if the unit test needs to run on real SGX hardware.
 */
@Tag("hardware")
abstract class HardwareTest {
    companion object {
        val spid = OpaqueBytes.parse(System.getProperty("conclave.test.spid"))
        val attestationKey = checkNotNull(System.getProperty("conclave.test.attestation-key"))
    }
}
