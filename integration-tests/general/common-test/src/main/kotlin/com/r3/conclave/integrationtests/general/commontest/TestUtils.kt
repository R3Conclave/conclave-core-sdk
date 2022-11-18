package com.r3.conclave.integrationtests.general.commontest

import com.r3.conclave.common.EnclaveMode
import com.r3.conclave.common.SHA256Hash
import com.r3.conclave.host.AttestationParameters
import com.r3.conclave.host.EnclaveHost
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assumptions.assumeThat
import org.bouncycastle.openssl.PEMKeyPair
import org.bouncycastle.openssl.PEMParser
import org.bouncycastle.openssl.jcajce.JcaPEMKeyConverter
import java.nio.file.Path
import java.security.MessageDigest
import java.security.interfaces.RSAPublicKey
import kotlin.io.path.absolutePathString
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.reader

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

    fun enclaveMode(): ITEnclaveMode = ITEnclaveMode.valueOf(System.getProperty("enclaveMode").uppercase())

    fun simulationOnlyTest() {
        assumeThat(enclaveMode()).isEqualTo(ITEnclaveMode.SIMULATION)
    }

    fun debugOnlyTest() {
        assumeThat(enclaveMode()).isEqualTo(ITEnclaveMode.DEBUG)
    }

    fun runtimeType(): RuntimeType = RuntimeType.valueOf(System.getProperty("runtimeType").uppercase())

    fun graalvmOnlyTest() {
        assumeThat(runtimeType()).isEqualTo(RuntimeType.GRAALVM)
    }

    fun gramineOnlyTest() {
        assumeThat(runtimeType()).isEqualTo(RuntimeType.GRAMINE)
    }

    /**
     * Generate an enclave signing key as per the Intel SGX documentation.
     */
    fun generateSigningKey(file: Path) {
        file.parent?.createDirectories()
        val exitCode = ProcessBuilder()
            .command(listOf("openssl", "genrsa", "-out", file.absolutePathString(), "-3", "3072"))
            .inheritIO()
            .start()
            .waitFor()
        assertThat(exitCode).isZero
    }

    fun readSigningKey(file: Path): RSAPublicKey {
        return PEMParser(file.reader()).use { pem ->
            JcaPEMKeyConverter().getKeyPair(pem.readObject() as PEMKeyPair).public as RSAPublicKey
        }
    }

    fun calculateMrsigner(key: RSAPublicKey): SHA256Hash {
        assertThat(key.publicExponent).isEqualTo(3)
        val modulusBytes = key.modulus.toByteArray().apply { reverse() }
        assertThat(modulusBytes).hasSize(385)
        assertThat(modulusBytes.last()).isEqualTo(0)
        return SHA256Hash.wrap(
            with(MessageDigest.getInstance("SHA-256")) {
                update(modulusBytes, 0, 384)
                digest()
            }
        )
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
