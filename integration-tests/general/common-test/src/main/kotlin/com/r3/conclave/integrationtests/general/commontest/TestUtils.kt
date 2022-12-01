package com.r3.conclave.integrationtests.general.commontest

import com.r3.conclave.common.EnclaveMode
import com.r3.conclave.common.internal.Cursor
import com.r3.conclave.common.internal.SgxEnclaveCss
import com.r3.conclave.host.AttestationParameters
import com.r3.conclave.host.EnclaveHost
import com.r3.conclave.integrationtests.general.common.ByteCursor
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assumptions.assumeThat
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption.REPLACE_EXISTING
import java.nio.file.attribute.PosixFilePermission.OWNER_EXECUTE
import kotlin.io.path.*

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

    val enclaveMode: ITEnclaveMode = ITEnclaveMode.valueOf(System.getProperty("enclaveMode").uppercase())

    val runtimeType: RuntimeType = RuntimeType.valueOf(System.getProperty("runtimeType").uppercase())

    fun simulationOnlyTest() {
        assumeThat(enclaveMode).isEqualTo(ITEnclaveMode.SIMULATION)
    }

    fun debugOnlyTest() {
        assumeThat(enclaveMode).isEqualTo(ITEnclaveMode.DEBUG)
    }

    fun graalvmOnlyTest() {
        assumeThat(runtimeType).isEqualTo(RuntimeType.GRAALVM)
    }

    fun gramineOnlyTest() {
        assumeThat(runtimeType).isEqualTo(RuntimeType.GRAMINE)
    }

    private val sgxSignTool: Path by lazy {
        val path = Files.createTempFile("sgx_sign", null)
        javaClass.getResourceAsStream("/sgx-tools/sgx_sign")!!.use {
            Files.copy(it, path, REPLACE_EXISTING)
        }
        path.setPosixFilePermissions(path.getPosixFilePermissions() + OWNER_EXECUTE)
        path
    }

    /**
     * Extract the enclave `SIGSTRUCT` from the given signed .so file.
     */
    fun getEnclaveSigstruct(enclaveFile: Path): ByteCursor<SgxEnclaveCss> {
        val cssFile = Files.createTempFile("enclave-css", ".bin")
        execCommand(
            sgxSignTool.absolutePathString(), "dump",
            "-enclave", enclaveFile.absolutePathString(),
            // We don't need this but sgx_sign still requires it be specified.
            "-dumpfile", "/dev/null",
            "-cssfile", cssFile.absolutePathString()
        )
        return Cursor.wrap(SgxEnclaveCss.INSTANCE, cssFile.readBytes())
    }

    fun execCommand(vararg command: String) {
        val exitCode = ProcessBuilder()
            .command(command.asList())
            .inheritIO()
            .start()
            .waitFor()
        assertThat(exitCode).isZero
    }

    /**
     * Represents the various modes the integration tests can run in.
     */
    enum class ITEnclaveMode {
        SIMULATION,
        DEBUG;

        fun toEnclaveMode(): EnclaveMode {
            return when (this) {
                SIMULATION -> EnclaveMode.SIMULATION
                DEBUG -> EnclaveMode.DEBUG
            }
        }
    }

    enum class RuntimeType {
        GRAALVM,
        GRAMINE
    }
}
