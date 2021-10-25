package com.r3.conclave.integrationtests.general.commontest

import com.r3.conclave.common.OpaqueBytes
import com.r3.conclave.host.AttestationParameters
import com.r3.conclave.host.EnclaveHost
import com.r3.conclave.host.MailCommand
import com.r3.conclave.integrationtests.general.common.tasks.EnclaveTestAction
import com.r3.conclave.integrationtests.general.common.tasks.decode
import com.r3.conclave.integrationtests.general.common.tasks.encode
import com.r3.conclave.mail.Curve25519PrivateKey
import com.r3.conclave.mail.PostOffice
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import java.nio.file.Paths
import java.util.*
import kotlin.io.path.div
import kotlin.io.path.exists

abstract class AbstractEnclaveActionTest(private val defaultEnclaveClassName: String) {
    constructor() : this("com.r3.conclave.integrationtests.general.defaultenclave.DefaultEnclave")

    @TempDir
    @JvmField
    //  Note, this can be overridden (in EnclaveRestartFileSystemTest class for example)
    var fileSystemFileTempDir: Path? = null

    companion object {
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

    private val enclaves = HashMap<String, EnclaveHostWrapper>()

    @AfterEach
    fun closeEnclaves() {
        synchronized(enclaves) {
            enclaves.values.forEach { it.enclaveHost.close() }
            enclaves.clear()
        }
    }

    fun enclaveHost(enclaveClassName: String = defaultEnclaveClassName): EnclaveHost {
        return getEnclaveHostWrapper(enclaveClassName).enclaveHost
    }

    fun restartEnclave(enclaveClassName: String = defaultEnclaveClassName) {
        synchronized(enclaves) {
            enclaves.getValue(enclaveClassName).restart()
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
        return MailClientImpl(getEnclaveHostWrapper(enclaveClassName))
    }

    interface MailClient {
        fun <R> deliverMail(action: EnclaveTestAction<R>, callback: ((ByteArray) -> ByteArray?)? = null): R
    }


    private fun getEnclaveHostWrapper(enclaveClassName: String): EnclaveHostWrapper {
        return synchronized(enclaves) {
            enclaves.computeIfAbsent(enclaveClassName) { EnclaveHostWrapper(enclaveClassName) }
        }
    }

    private inner class EnclaveHostWrapper(
        private val enclaveClassName: String
    ) {
        val postedMail = HashMap<String, ByteArray>()
        private var sealedState: ByteArray? = null
        var enclaveHost = newEnclaveHost()
        val clients = ArrayList<MailClientImpl>()


        private fun newEnclaveHost(): EnclaveHost {
            enclaveHost = EnclaveHost.load(enclaveClassName)
            //  Note that fileSystemFileTempDir can be overridden (in EnclaveRestartFileSystem class for example),
            //    and can become null
            val fileSystemFilePath = if (fileSystemFileTempDir == null) {
                null
            } else {
                fileSystemFileTempDir?.resolve("test.disk")
            }
            enclaveHost.start(
                getAttestationParams(enclaveHost),
                sealedState,
                fileSystemFilePath
            ) { commands ->
                for (command in commands) {
                    when (command) {
                        is MailCommand.PostMail -> postedMail[command.routingHint!!] = command.encryptedBytes
                        is MailCommand.StoreSealedState -> sealedState = command.sealedState
                    }
                }
            }
            return enclaveHost
        }

        fun restart() {
            enclaveHost.close()
            enclaveHost = newEnclaveHost()
            clients.forEach { it.enclaveRestarted() }
        }
    }

    private class MailClientImpl(private val enclave: EnclaveHostWrapper) : MailClient {
        private val id = UUID.randomUUID().toString()
        private val privatekey = Curve25519PrivateKey.random()
        private var postOffice = createPostOffice()

        init {
            enclave.clients += this
        }

        override fun <R> deliverMail(action: EnclaveTestAction<R>, callback: ((ByteArray) -> ByteArray?)?): R {
            val encodedAction = encode(EnclaveTestAction.serializer(action.resultSerializer()), action)
            val encryptedAction = postOffice.encryptMail(encodedAction)
            if (callback != null) {
                enclave.enclaveHost.deliverMail(encryptedAction, id, callback)
            } else {
                enclave.enclaveHost.deliverMail(encryptedAction, id)
            }
            val encryptedResult = checkNotNull(enclave.postedMail.remove(id))
            val resultMail = postOffice.decryptMail(encryptedResult)
            return decode(action.resultSerializer(), resultMail.bodyAsBytes)
        }

        fun enclaveRestarted() {
            val old = postOffice
            postOffice = createPostOffice()
            // TODO We wouldn't need to do this if we had a client layer API above PostOffice: https://r3-cev.atlassian.net/browse/CON-617
            postOffice.lastSeenStateId = old.lastSeenStateId
        }

        private fun createPostOffice(): PostOffice {
            return enclave.enclaveHost.enclaveInstanceInfo.createPostOffice(privatekey, "default")
        }
    }
}
