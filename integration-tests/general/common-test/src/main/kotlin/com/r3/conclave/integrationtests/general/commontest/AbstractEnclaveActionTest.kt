package com.r3.conclave.integrationtests.general.commontest

import com.r3.conclave.common.OpaqueBytes
import com.r3.conclave.host.AttestationParameters
import com.r3.conclave.host.EnclaveHost
import com.r3.conclave.integrationtests.general.common.tasks.EnclaveTestAction
import com.r3.conclave.integrationtests.general.common.tasks.decode
import com.r3.conclave.integrationtests.general.common.tasks.encode
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.io.TempDir
import java.net.ServerSocket
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.concurrent.CountDownLatch
import kotlin.concurrent.thread
import kotlin.io.path.exists

abstract class AbstractEnclaveActionTest(
    private val defaultEnclaveClassName: String = "com.r3.conclave.integrationtests.general.defaultenclave.DefaultEnclave"
) {
    /**
     * Set this to null if a test needs to initialise a host without the enclave file system path.
     */
    @TempDir
    @JvmField
    var fileSystemFileTempDir: Path? = null

    var useKds = false

    companion object {
        private val kdsPort by lazy(::startKds)

        private fun startKds(): Int {
            val java = Paths.get(System.getProperty("java.home"), "bin", "java").toString()
            val kdsJar = checkNotNull(System.getProperty("kdsJar"))
            val randomPort = ServerSocket(0).use { it.localPort }
            val kdsCmd = listOf(java, "-jar", kdsJar, "--server.port=$randomPort")
            println("Starting KDS: $kdsCmd")
            val process = ProcessBuilder(kdsCmd).redirectErrorStream(true).start()

            // Kill the KDS sub-process when the test worker process is done.
            Runtime.getRuntime().addShutdownHook(Thread(process::destroyForcibly))

            // Start a background thread that prints the KDS's log output to help with debugging and signals when it's
            // ready to accept requests.
            val kdsReadySignal = CountDownLatch(1)
            thread(isDaemon = true) {
                val kdsOutput = process.inputStream.bufferedReader()
                while (true) {
                    val line = kdsOutput.readLine()
                    if (line == null) {
                        println("KDS EOF")
                        break
                    }
                    println("KDS> $line")
                    if ("Started KdsApplication.Companion in " in line) {
                        kdsReadySignal.countDown()
                    }
                }
            }

            println("Waiting for KDS to be ready...")
            kdsReadySignal.await()
            println("KDS ready")
            return randomPort
        }

        fun getAttestationParams(enclaveHost: EnclaveHost): AttestationParameters? {
            return if (enclaveHost.enclaveMode.isHardware) getHardwareAttestationParams() else null
        }

        fun getHardwareAttestationParams(): AttestationParameters {
            return when {
                // EPID vs DCAP can be detected because the drivers are different and have different names.
                Paths.get("/dev/isgx").exists() -> {
                    val spid = OpaqueBytes.parse(System.getProperty("conclave.spid"))
                    val attestationKey = checkNotNull(System.getProperty("conclave.attestation-key"))
                    AttestationParameters.EPID(spid, attestationKey)
                }
                Paths.get("/dev/sgx/enclave").exists() -> {
                    AttestationParameters.DCAP()
                }
                else -> throw UnsupportedOperationException(
                    "SGX does not appear to be available on this machine. Check kernel drivers."
                )
            }
        }

        fun <R> callEnclave(
            enclaveHost: EnclaveHost,
            action: EnclaveTestAction<R>,
            callback: ((ByteArray) -> ByteArray?)? = null
        ): R {
            val encodedAction = encode(EnclaveTestAction.serializer(action.resultSerializer()), action)
            val encodedResult = if (callback != null) {
                enclaveHost.callEnclave(encodedAction, callback)
            } else {
                enclaveHost.callEnclave(encodedAction)
            }
            assertThat(encodedResult).isNotNull
            return decode(action.resultSerializer(), encodedResult!!)
        }
    }

    private val enclaveTransports = HashMap<String, TestEnclaveTransport>()

    @AfterEach
    fun closeEnclaves() {
        synchronized(enclaveTransports) {
            enclaveTransports.values.forEach { it.close() }
            enclaveTransports.clear()
        }
    }

    fun enclaveHost(enclaveClassName: String = defaultEnclaveClassName): EnclaveHost {
        return getEnclaveTransport(enclaveClassName).enclaveHost
    }

    fun restartEnclave(enclaveClassName: String = defaultEnclaveClassName) {
        synchronized(enclaveTransports) {
            enclaveTransports.getValue(enclaveClassName).restartEnclave()
        }
    }

    fun <R> callEnclave(
        action: EnclaveTestAction<R>,
        enclaveClassName: String = defaultEnclaveClassName,
        callback: ((ByteArray) -> ByteArray?)? = null
    ): R {
        return callEnclave(enclaveHost(enclaveClassName), action, callback)
    }

    fun newMailClient(enclaveClassName: String = defaultEnclaveClassName): MailClient {
        return MailClientImpl(getEnclaveTransport(enclaveClassName))
    }

    interface MailClient {
        fun <R> deliverMail(action: EnclaveTestAction<R>, callback: ((ByteArray) -> ByteArray?)? = null): R
    }


    private fun getEnclaveTransport(enclaveClassName: String): TestEnclaveTransport {
        return synchronized(enclaveTransports) {
            enclaveTransports.computeIfAbsent(enclaveClassName) {
                val enclaveFileSystemFile = fileSystemFileTempDir?.resolve("$enclaveClassName.disk")
                val kdsUrl = if (useKds) "http://localhost:$kdsPort" else null
                val transport = object : TestEnclaveTransport(enclaveClassName, enclaveFileSystemFile, kdsUrl) {
                    override val attestationParameters: AttestationParameters? get() = getAttestationParams(enclaveHost)
                }
                transport.startEnclave()
                transport
            }
        }
    }

    private class MailClientImpl(private val enclaveTransport: TestEnclaveTransport) : MailClient {
        private val enclaveClient = enclaveTransport.startNewClient()
        private val clientConnection = enclaveClient.clientConnection as TestEnclaveTransport.ClientConnection

        override fun <R> deliverMail(action: EnclaveTestAction<R>, callback: ((ByteArray) -> ByteArray?)?): R {
            val encodedAction = encode(EnclaveTestAction.serializer(action.resultSerializer()), action)
            val resultMail = if (callback == null) {
                enclaveClient.sendMail(encodedAction)
            } else {
                // This is a bit of a hack. EnclaveClient doesn't support a callback, for obvious reasons, and so we
                // have to manually encrypt the request and decrypt any response. By not using the EnclaveClient here
                // means we're not exercising rollback detection, for example.
                val postOffice = enclaveClient.postOffice("default")
                val mailRequestBytes = postOffice.encryptMail(encodedAction)
                val mailResponseBytes = enclaveTransport.enclaveHostService.deliverMail(
                    mailRequestBytes,
                    clientConnection.id,
                    callback
                )
                mailResponseBytes?.let(postOffice::decryptMail)
            }
            checkNotNull(resultMail)
            return decode(action.resultSerializer(), resultMail.bodyAsBytes)
        }
    }
}
