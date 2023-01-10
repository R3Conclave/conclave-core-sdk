package com.r3.conclave.integrationtests.general.commontest

import com.r3.conclave.host.AttestationParameters
import com.r3.conclave.host.EnclaveHost
import com.r3.conclave.integrationtests.general.common.tasks.EnclaveTestAction
import com.r3.conclave.integrationtests.general.common.tasks.decode
import com.r3.conclave.integrationtests.general.common.tasks.encode
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path

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

    // Used to disable teardown for the `exception is thrown if too many threads are requested` test
    // TODO: CON-1244 Figure out why this test hangs, then remove this teardown logic.
    var doEnclaveTeardown = true

    companion object {
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
        // Disable teardown for the `exception is thrown if too many threads are requested` test
        // TODO: CON-1244 Figure out why this test hangs, then remove this teardown logic.
        if (doEnclaveTeardown) {
            synchronized(enclaveTransports) {
                enclaveTransports.values.forEach { it.close() }
                enclaveTransports.clear()
            }
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

    fun getFileSystemFilePath(enclaveClassName: String): Path? {
        return fileSystemFileTempDir?.resolve("$enclaveClassName.disk")
    }

    private fun getEnclaveTransport(enclaveClassName: String): TestEnclaveTransport {
        return synchronized(enclaveTransports) {
            enclaveTransports.computeIfAbsent(enclaveClassName) {
                val enclaveFileSystemFile = getFileSystemFilePath(enclaveClassName)
                val kdsUrl = if (useKds) "http://localhost:${TestKds.testKdsPort}" else null
                val transport = object : TestEnclaveTransport(enclaveClassName, enclaveFileSystemFile, kdsUrl) {
                    override val attestationParameters: AttestationParameters? get() = TestUtils.getAttestationParams(enclaveHost)
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
