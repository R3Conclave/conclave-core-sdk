package com.r3.conclave.integrationtests.general.tests

import com.r3.conclave.common.EnclaveMode
import com.r3.conclave.common.OpaqueBytes
import com.r3.conclave.host.AttestationParameters
import com.r3.conclave.host.EnclaveHost
import com.r3.conclave.host.MailCommand
import com.r3.conclave.integrationtests.general.common.tasks.Deserializer
import com.r3.conclave.integrationtests.general.common.tasks.JvmTestTask
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import java.nio.ByteBuffer
import java.nio.file.Files
import java.nio.file.Paths
import java.util.concurrent.CompletableFuture
import java.util.function.Consumer
import kotlin.concurrent.thread

open class JvmTest(private val enclaveClassName: String) {
    constructor(threadSafe: Boolean) : this(
        if (threadSafe)
            "com.r3.conclave.integrationtests.general.enclave.ThreadSafeEnclave"
        else
            "com.r3.conclave.integrationtests.general.enclave.NonThreadSafeEnclave"
    )

    companion object {
        fun getHardwareAttestationParams(): AttestationParameters {
            return when {
                // EPID vs DCAP can be detected because the drivers are different and have different names.
                Files.exists(Paths.get("/dev/isgx")) -> {
                    val spid = OpaqueBytes.parse(System.getProperty("conclave.spid"))
                    val attestationKey = checkNotNull(System.getProperty("conclave.attestation-key"))
                    AttestationParameters.EPID(spid, attestationKey)
                }
                Files.exists(Paths.get("/dev/sgx/enclave")) -> {
                    AttestationParameters.DCAP()
                }
                else -> throw UnsupportedOperationException(
                    "SGX does not appear to be available on this machine. Check kernel drivers."
                )
            }
        }
    }

    lateinit var enclaveHost : EnclaveHost
    var closeHost = true

    var mailCommands = mutableListOf<MailCommand>()

    @BeforeEach
    fun beforeEach() {
        enclaveHost = EnclaveHost.load(enclaveClassName)
        val attestationParameters = when(enclaveHost.enclaveMode){
            EnclaveMode.RELEASE, EnclaveMode.DEBUG -> getHardwareAttestationParams()
            else -> null
        }
        enclaveHost.start(attestationParameters){
            mailCommands.addAll(it)
        }
        closeHost = true
    }

    @AfterEach
    fun afterEach() {
        if (closeHost)
            enclaveHost.close()
    }

    fun <T, R> sendMessage(message: T): R where T : Deserializer<R>, T : JvmTestTask {
        val reply = enclaveHost.callEnclave(message.encode())
        assertThat(reply).isNotNull
        return message.deserialize(reply!!)
    }

    fun <T, R> sendMessage(message: T, callback: (ByteArray) -> ByteArray?): R where T : Deserializer<R>, T : JvmTestTask {
        val reply = enclaveHost.callEnclave(message.encode(), callback)
        assertThat(reply).isNotNull
        return message.deserialize(reply!!)
    }

    fun <T> sendMessageNoReply(message: T) where T : JvmTestTask {
        enclaveHost.callEnclave(message.encode())
    }

}