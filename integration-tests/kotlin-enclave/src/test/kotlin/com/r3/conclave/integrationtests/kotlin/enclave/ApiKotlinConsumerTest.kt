package com.r3.conclave.integrationtests.kotlin.enclave

import com.r3.conclave.host.EnclaveHost
import com.r3.conclave.host.MailCommand
import com.r3.conclave.mail.Curve25519KeyPairGenerator
import com.r3.conclave.mail.EnclaveMail
import com.r3.conclave.mail.MutableMail
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import java.security.KeyPair

/**
 * This test makes sure that the shading of Kotlin into the Conclave SDK does not prevent an app from being written in
 * Kotlin.
 */
class ApiKotlinConsumerTest {
    private lateinit var host: EnclaveHost

    @AfterEach
    fun cleanUp() {
        if (::host.isInitialized) {
            host.close()
        }
    }

    @Test
    fun `APIs work in Kotlin`() {
        host = EnclaveHost.load("com.r3.conclave.integrationtests.kotlin.enclave.KotlinEnclave")

        val postedMail = ArrayList<ByteArray>()

        host.start(null) { commands ->
            postedMail += (commands.single() as MailCommand.PostMail).encryptedBytes
        }

        val responseForHost: ByteArray? = host.callEnclave(byteArrayOf(9)) { fromEnclave ->
            host.callEnclave(fromEnclave + 8)
        }
        assertThat(responseForHost).isEqualTo(byteArrayOf(9, 1, 8, 2))

        val mutableMail: MutableMail = host.enclaveInstanceInfo.createMail("abc".toByteArray())
        val keyPair: KeyPair = Curve25519KeyPairGenerator().generateKeyPair()
        mutableMail.privateKey = keyPair.private
        mutableMail.topic = ApiKotlinConsumerTest::class.java.simpleName
        val encryptedMail: ByteArray = mutableMail.encrypt()

        host.deliverMail(1, encryptedMail, null) { fromEnclave ->
            fromEnclave + fromEnclave
        }
        assertThat(postedMail).hasSize(1)
        val responseForClient: EnclaveMail = host.enclaveInstanceInfo.decryptMail(postedMail[0], keyPair.private)
        assertThat(responseForClient.bodyAsBytes).isEqualTo("cbacba".toByteArray())
    }
}
