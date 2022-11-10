package com.r3.conclave.integrationtests.general.commontest

import com.r3.conclave.common.EnclaveMode
import com.r3.conclave.host.AttestationParameters
import com.r3.conclave.host.EnclaveHost
import org.junit.jupiter.api.Assumptions.assumeTrue
import java.nio.file.Path
import kotlin.io.path.exists

object TestUtils {
    fun getAttestationParams(enclaveHost: EnclaveHost): AttestationParameters? {
        return if (enclaveHost.enclaveMode.isHardware) getHardwareAttestationParams() else null
    }

    private fun getHardwareAttestationParams(): AttestationParameters {
        return if (Path.of("/dev/sgx_enclave").exists() || Path.of("/dev/sgx/enclave").exists()) {
            AttestationParameters.DCAP()
        } else {
            throw UnsupportedOperationException(
                "SGX does not appear to be available on this machine. Check kernel drivers."
            )
        }
    }

    fun simulationOnlyTest() {
        assumeTrue(EnclaveMode.valueOf(System.getProperty("enclaveMode").uppercase()) == EnclaveMode.SIMULATION)
    }

    fun debugOnlyTest() {
        assumeTrue(EnclaveMode.valueOf(System.getProperty("enclaveMode").uppercase()) == EnclaveMode.DEBUG)
    }

    fun graalvmOnlyTest() {
        assumeTrue(System.getProperty("runtimeType") == "graalvm")
    }

    fun gramineOnlyTest() {
        assumeTrue(System.getProperty("runtimeType") == "gramine")
    }
}
