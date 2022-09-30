package com.r3.conclave.integrationtests.kotlin.enclave

import com.r3.conclave.host.EnclaveHost
import com.r3.conclave.host.MailCommand
import com.r3.conclave.mail.Curve25519PrivateKey
import com.r3.conclave.mail.EnclaveMail
import com.r3.conclave.mail.PostOffice
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import java.security.PrivateKey

/**
 * This test makes sure that the shading of Kotlin into the Conclave libraries does not prevent an app from being
 * written in Kotlin.
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

        val capturedCommands = ArrayList<MailCommand>()

        host.start(null, null, null) { commands ->
            capturedCommands += commands
        }

        val responseForHost: ByteArray? = host.callEnclave(byteArrayOf(9)) { fromEnclave ->
            host.callEnclave(fromEnclave + 8)
        }
        assertThat(responseForHost).isEqualTo(byteArrayOf(9, 1, 8, 2))

        val privateKey: PrivateKey = Curve25519PrivateKey.random()
        val postOffice: PostOffice = host.enclaveInstanceInfo.createPostOffice(privateKey, ApiKotlinConsumerTest::class.java.simpleName)
        val encryptedMail: ByteArray = postOffice.encryptMail("abc".toByteArray())

        host.deliverMail(encryptedMail, null) { fromEnclave ->
            fromEnclave + fromEnclave
        }

        val postMail = capturedCommands.filterIsInstance<MailCommand.PostMail>().single()
        val responseForClient: EnclaveMail = postOffice.decryptMail(postMail.encryptedBytes)
        assertThat(responseForClient.bodyAsBytes).isEqualTo("cbacba".toByteArray())
    }
}
