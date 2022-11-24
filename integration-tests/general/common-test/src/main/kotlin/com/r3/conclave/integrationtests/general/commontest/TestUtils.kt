package com.r3.conclave.integrationtests.general.commontest

import com.r3.conclave.common.EnclaveMode
import com.r3.conclave.common.SHA256Hash
import com.r3.conclave.common.internal.Cursor
import com.r3.conclave.common.internal.SgxMetadataEnclaveCss
import com.r3.conclave.host.AttestationParameters
import com.r3.conclave.host.EnclaveHost
import com.r3.conclave.integrationtests.general.common.ByteCursor
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assumptions.assumeThat
import org.bouncycastle.openssl.PEMKeyPair
import org.bouncycastle.openssl.PEMParser
import org.bouncycastle.openssl.jcajce.JcaPEMKeyConverter
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardCopyOption.REPLACE_EXISTING
import java.nio.file.attribute.PosixFilePermission.OWNER_EXECUTE
import java.security.MessageDigest
import java.security.interfaces.RSAPublicKey
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipInputStream
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

    fun ZipFile.assertEntryExists(name: String): ZipEntry {
        val entry = getEntry(name)
        assertThat(entry).isNotNull
        return entry
    }

    fun ZipFile.assertEntryContents(name: String, block: (InputStream) -> Unit) {
        val entry = assertEntryExists(name)
        getInputStream(entry).use(block)
    }

    fun InputStream.readZipEntryNames(): List<String> {
        return ZipInputStream(this).use { zip ->
            generateSequence { zip.nextEntry?.name }.toList()
        }
    }

    /**
     * Generate an enclave signing key as per the Intel SGX documentation.
     */
    fun generateSigningKey(file: Path) {
        file.parent?.createDirectories()
        execCommand("openssl", "genrsa", "-out", file.absolutePathString(), "-3", "3072")
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
        return SHA256Hash.wrap(
            with(MessageDigest.getInstance("SHA-256")) {
                update(modulusBytes, 0, 384)  // Ignore the sign byte which is added by BigInteger.toByteArray()
                digest()
            }
        )
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
     * Extract the enclave metadata from the given signed .so file.
     */
    fun getEnclaveMetadata(enclaveFile: Path): ByteCursor<SgxMetadataEnclaveCss> {
        // Get the location of the sgx_sign tool directly from the Gradle resources directory. This only works
        // because the integration tests are not run via jars. If they were then we'd need to copy the tool into a
        // temp file.
        val cssFile = Files.createTempFile("enclave-css", null)
        execCommand(
            sgxSignTool.absolutePathString(), "dump",
            "-enclave", enclaveFile.absolutePathString(),
            // We don't need this but sgx_sign still requires it be specified.
            "-dumpfile", "/dev/null",
            "-cssfile", cssFile.absolutePathString()
        )
        return Cursor.wrap(SgxMetadataEnclaveCss.INSTANCE, cssFile.readBytes())
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
