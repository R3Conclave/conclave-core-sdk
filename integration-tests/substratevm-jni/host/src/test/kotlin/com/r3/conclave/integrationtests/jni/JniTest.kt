package com.r3.conclave.integrationtests.jni

import com.r3.conclave.common.OpaqueBytes
import com.r3.conclave.host.AttestationParameters
import com.r3.conclave.host.EnclaveHost
import com.r3.conclave.integrationtests.jni.tasks.Deserializer
import com.r3.conclave.integrationtests.jni.tasks.GraalJniTask
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll

open class JniTest {
    companion object {
        private var referenceCounter = 0
        lateinit var enclaveHost : EnclaveHost

        @BeforeAll
        @JvmStatic
        fun setup() {
            synchronized(referenceCounter) {
                if (++referenceCounter > 0) {
                    val spid = OpaqueBytes.parse(System.getProperty("conclave.spid"))
                    val attestationKey = checkNotNull(System.getProperty("conclave.attestation-key"))
                    enclaveHost = EnclaveHost.load("com.r3.conclave.integrationtests.jni.JniEnclave")
                    enclaveHost.start(AttestationParameters.EPID(spid, attestationKey), null)
                }
            }
        }

        @AfterAll
        @JvmStatic
        fun destroy() {
            synchronized(referenceCounter) {
                if (--referenceCounter <= 0 && Companion::enclaveHost.isInitialized) {
                    enclaveHost.close()
                }
            }
        }

        /**
         * Sends a serialized [GraalJniTask] of type [T] to the enclave, receives a serialized response and
         * returns an instance of the deserialized response of type [R]
         * @param message The message to be sent to the enclave
         * @return An instance of the message response of type [R]
         */
        fun <T, R> sendMessage(message: T): R where T : Deserializer<R>, T : GraalJniTask {
            val reply = enclaveHost.callEnclave(message.encode())
            assertThat(reply).isNotNull
            return message.deserialize(reply!!)
        }
    }
}
