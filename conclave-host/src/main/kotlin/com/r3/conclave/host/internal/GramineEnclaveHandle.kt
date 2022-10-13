package com.r3.conclave.host.internal

import com.r3.conclave.common.*
import com.r3.conclave.common.internal.*
import com.r3.conclave.common.internal.attestation.MockAttestation
import com.r3.conclave.mail.Curve25519PrivateKey
import java.io.File
import java.io.IOException
import java.net.URL
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption.REPLACE_EXISTING
import java.time.Instant
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern
import kotlin.io.path.div
import kotlin.random.Random


//  TODO: Refactor it to support multiple enclaves and without defaulting to Mock mode
class GramineEnclaveHandle(
    override val enclaveMode: EnclaveMode,
    override val enclaveClassName: String,
    private val manifestUrl: URL,
    private val jarUrl: URL
) : EnclaveHandle {
    private lateinit var processGramineDirect: Process
    private val enclaveDirectory: Path

    private val callInterfaceConnector = MockCallInterfaceConnector()
    override val enclaveInterface = GramineHostEnclaveInterface(callInterfaceConnector)

    init {
        val classNamePath = enclaveClassName.substringAfter("!.")//.replace(".", "/")
        enclaveDirectory = Files.createTempDirectory("$classNamePath-gramine")
        copyGramineFilesToWorkingDirectory()
    }

    companion object {
        const val GRAMINE_ENCLAVE_JAR_NAME = "enclave-shadow.jar"
        val GRAMINE_MANIFEST_PATTERN: Pattern = Pattern.compile("""^(.+)-(simulation|debug|release)/(bash\.manifest)$""")

        fun getDummyAttestation(): EnclaveInstanceInfoImpl {
            val signingKeyPair = SignatureSchemeEdDSA().generateKeyPair()
            val encryptionPrivateKey = Curve25519PrivateKey.random()
            val measurement = SHA256Hash.wrap(Random.nextBytes(32))
            val cpuSvn = OpaqueBytes(Random.nextBytes(16))
            val mrsigner = SHA256Hash.wrap(Random.nextBytes(32))
            val isvProdId = 1
            val isvSvn = 1
            val reportBody = Cursor.allocate(SgxReportBody).apply {
                this[SgxReportBody.cpuSvn] = cpuSvn.buffer()
                this[SgxReportBody.mrenclave] = measurement.buffer()
                this[SgxReportBody.mrsigner] = mrsigner.buffer()
                this[SgxReportBody.isvProdId] = isvProdId
                this[SgxReportBody.isvSvn] = isvSvn
                this[SgxReportBody.reportData] =
                    SHA512Hash.hash(signingKeyPair.public.encoded + encryptionPrivateKey.publicKey.encoded).buffer()
            }

            val timestamp = Instant.now()

            return EnclaveInstanceInfoImpl(signingKeyPair.public, encryptionPrivateKey.publicKey,
                MockAttestation(timestamp, reportBody.asReadOnly(), false))
        }

        private val logger = loggerFor<GramineEnclaveHandle>()
    }

    override fun initialise() {

        processGramineDirect = ProcessBuilder()
            .inheritIO()
            .directory(enclaveDirectory.toFile())
            .command("gramine-direct", "bash", "-c", """echo "Gramine bash 'enclave' started" && sleep 10000 && echo "Slept enough"""")
            .start()
    }

    override fun destroy() {
        if (!::processGramineDirect.isInitialized) return
        processGramineDirect.destroy()
        processGramineDirect.waitFor(10L, TimeUnit.SECONDS)
        if (processGramineDirect.isAlive) {
            processGramineDirect.destroyForcibly()
        }

        try {
            enclaveDirectory.toFile().deleteRecursively()
        } catch (e: IOException) {
            logger.debug("Unable to delete temp directory $enclaveDirectory", e)
        }
    }

    private fun copyGramineFilesToWorkingDirectory() {
        val manifestName = File(manifestUrl.file).name
        val jarName = File(jarUrl.file).name

        manifestUrl.openStream().use {
            Files.copy(it, enclaveDirectory / manifestName, REPLACE_EXISTING)
        }

        manifestUrl.openStream().use {
            Files.copy(it, enclaveDirectory / jarName, REPLACE_EXISTING)
        }
    }

    override val mockEnclave: Any get() {
        throw IllegalStateException("The enclave instance can only be accessed in mock mode.")
    }
}
