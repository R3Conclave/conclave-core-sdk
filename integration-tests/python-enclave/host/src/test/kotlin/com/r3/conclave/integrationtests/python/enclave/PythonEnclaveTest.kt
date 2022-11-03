package com.r3.conclave.integrationtests.python.enclave

import com.r3.conclave.host.AttestationParameters
import com.r3.conclave.host.EnclaveHost
import com.r3.conclave.host.MailCommand.PostMail
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIfSystemProperty

@EnabledIfSystemProperty(named = "runtimeType", matches = "gramine")
class PythonEnclaveTest {
    private lateinit var enclaveHost: EnclaveHost

    private val postedMail = ArrayList<PostMail>()

    @BeforeEach
    fun start() {
        enclaveHost = EnclaveHost.load()
        enclaveHost.start(AttestationParameters.DCAP(), null, null) { commands ->
            postedMail += commands.filterIsInstance<PostMail>()
        }
    }

    @AfterEach
    fun close() {
        if (::enclaveHost.isInitialized) {
            enclaveHost.close()
        }
    }

    @Test
    fun receive_from_untrusted_host() {
        val data = "hello world".toByteArray()
        val signature = enclaveHost.callEnclave(data)!!
        with(enclaveHost.enclaveInstanceInfo.verifier()) {
            update(data)
            assertThat(verify(signature)).isTrue
        }
    }

    @Test
    fun receive_enclave_mail() {
        val clientPostOffice = enclaveHost.enclaveInstanceInfo.createPostOffice()
        val mailRequest = clientPostOffice.encryptMail("12345".toByteArray())
        enclaveHost.deliverMail(mailRequest, null)
        val mailResponse = postedMail.single().encryptedBytes
        assertThat(clientPostOffice.decryptMail(mailResponse).bodyAsBytes).isEqualTo("54321".toByteArray())
    }
}
