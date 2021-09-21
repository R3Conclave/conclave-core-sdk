package com.r3.conclave.integrationtests.general.commontest

import com.r3.conclave.common.OpaqueBytes
import com.r3.conclave.host.AttestationParameters
import com.r3.conclave.host.EnclaveHost
import com.r3.conclave.host.MailCommand
import com.r3.conclave.integrationtests.general.common.tasks.EnclaveTestAction
import com.r3.conclave.integrationtests.general.common.tasks.decode
import com.r3.conclave.integrationtests.general.common.tasks.encode
import com.r3.conclave.mail.PostOffice
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import java.nio.file.Paths
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import kotlin.io.path.exists

abstract class AbstractEnclaveActionTest(private val defaultEnclaveClassName: String) {
    constructor() : this("com.r3.conclave.integrationtests.general.defaultenclave.DefaultEnclave")

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

    private val enclaveHosts = ConcurrentHashMap<String, EnclaveHost>()
    private val postedMail = ConcurrentHashMap<String, ByteArray>()

    @AfterEach
    fun closeEnclaves() {
        enclaveHosts.values.forEach(EnclaveHost::close)
    }

    fun enclaveHost(enclaveClassName: String = defaultEnclaveClassName): EnclaveHost {
        return enclaveHosts.computeIfAbsent(enclaveClassName) {
            val enclaveHost = EnclaveHost.load(enclaveClassName)
            enclaveHost.start(getAttestationParams(enclaveHost)) { commands ->
                for (command in commands) {
                    if (command is MailCommand.PostMail) {
                        postedMail[command.routingHint!!] = command.encryptedBytes
                    }
                }
            }
            enclaveHost
        }
    }

    fun <R> callEnclave(
        action: EnclaveTestAction<R>,
        enclaveClassName: String = defaultEnclaveClassName,
        callback: ((ByteArray) -> ByteArray?)? = null
    ): R {
        return callEnclave(enclaveHost(enclaveClassName), action, callback)
    }

    fun <R> deliverMail(
        postOffice: PostOffice,
        action: EnclaveTestAction<R>,
        enclaveClassName: String = defaultEnclaveClassName,
        callback: ((ByteArray) -> ByteArray?)? = null
    ): R {
        val encodedAction = encode(EnclaveTestAction.serializer(action.resultSerializer()), action)
        val encryptedAction = postOffice.encryptMail(encodedAction)
        val routingHint = UUID.randomUUID().toString()
        val enclaveHost = enclaveHost(enclaveClassName)
        if (callback != null) {
            enclaveHost.deliverMail(0, encryptedAction, routingHint, callback)
        } else {
            enclaveHost.deliverMail(0, encryptedAction, routingHint)
        }
        val encryptedResult = checkNotNull(postedMail.remove(routingHint))
        val resultMail = postOffice.decryptMail(encryptedResult)
        return decode(action.resultSerializer(), resultMail.bodyAsBytes)
    }
}
