package com.r3.conclave.client

import com.r3.conclave.enclave.Enclave
import com.r3.conclave.host.EnclaveHost
import com.r3.conclave.host.MailCommand
import com.r3.conclave.host.internal.createMockHost
import com.r3.conclave.mail.Curve25519PrivateKey
import com.r3.conclave.mail.EnclaveMail
import com.r3.conclave.mail.PostOffice
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class PostOfficeBuilderTest {
    private lateinit var enclaveHost: EnclaveHost
    private var enclaveResponse: ByteArray? = null

    @BeforeEach
    fun start() {
        enclaveHost = createMockHost(ReverseEnclave::class.java, null, null)
        enclaveHost.start(null, null, null, null) { mailCommands ->
            enclaveResponse = mailCommands.filterIsInstance<MailCommand.PostMail>().single().encryptedBytes
        }
    }

    @AfterEach
    fun close() {
        if (::enclaveHost.isInitialized) {
            enclaveHost.close()
        }
    }

    @Test
    fun forEnclaveInstance() {
        val postOffice = PostOfficeBuilder.forEnclaveInstance(enclaveHost.enclaveInstanceInfo).build()
        assertThat(postOffice.destinationPublicKey).isEqualTo(enclaveHost.enclaveInstanceInfo.encryptionKey)
        assertThat(deliverAndReceiveMail(postOffice, "123")).isEqualTo("321")
        assertThat(deliverAndReceiveMail(postOffice, "456")).isEqualTo("654")
    }

    @Test
    fun topic() {
        val builder = PostOfficeBuilder.forEnclaveInstance(enclaveHost.enclaveInstanceInfo)
        assertThat(builder.build().topic).isEqualTo("default")
        assertThat(builder.setTopic("topic123").build().topic).isEqualTo("topic123")
    }

    @Test
    fun `new random sender private key is used for each build`() {
        val builder = PostOfficeBuilder.forEnclaveInstance(enclaveHost.enclaveInstanceInfo)
        val postOffice1 = builder.build()
        val postOffice2 = builder.build()
        assertThat(postOffice1.senderPrivateKey).isNotEqualTo(postOffice2.senderPrivateKey)
    }

    @Test
    fun setSenderPrivateKey() {
        val builder = PostOfficeBuilder.forEnclaveInstance(enclaveHost.enclaveInstanceInfo)
        val privateKey = Curve25519PrivateKey.random()
        val postOffice = builder.setSenderPrivateKey(privateKey).build()
        assertThat(postOffice.senderPrivateKey).isEqualTo(privateKey)
    }

    private fun deliverAndReceiveMail(postOffice: PostOffice, payload: String): String {
        val encryptedMail = postOffice.encryptMail(payload.toByteArray())
        enclaveHost.deliverMail(encryptedMail, null)
        val decryptedMailResponse = postOffice.decryptMail(enclaveResponse!!)
        return decryptedMailResponse.bodyAsBytes.decodeToString()
    }

    private class ReverseEnclave : Enclave() {
        override fun receiveMail(mail: EnclaveMail, routingHint: String?) {
            postMail(postOffice(mail).encryptMail(mail.bodyAsBytes.reversedArray()), routingHint)
        }
    }
}
