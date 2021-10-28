package com.r3.conclave.template.enclave

import com.r3.conclave.host.EnclaveHost
import com.r3.conclave.host.MailCommand
import com.r3.conclave.mail.PostOffice
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class TemplateEnclaveKotlinTest {
    lateinit var result: ByteArray
    lateinit var postOffice: PostOffice

    @Test
    fun `first test`() {
        val mockHost = EnclaveHost.load("com.r3.conclave.template.enclave.TemplateEnclaveKotlin")

        mockHost.start(null, null, null) { commands ->
            commands.forEach { command ->
                when (command) {
                    is MailCommand.PostMail -> {
                        result = postOffice.decryptMail(command.encryptedBytes).bodyAsBytes
                    }
                }
            }
        }

        postOffice = mockHost.enclaveInstanceInfo.createPostOffice()
        val mail = postOffice.encryptMail("abc".toByteArray())
        mockHost.deliverMail(mail, "test")

        assertEquals("321", result.decodeToString())
    }
}