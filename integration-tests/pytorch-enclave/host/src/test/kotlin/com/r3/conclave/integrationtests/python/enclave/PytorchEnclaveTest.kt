package com.r3.conclave.integrationtests.python.enclave

import com.r3.conclave.host.AttestationParameters
import com.r3.conclave.host.EnclaveHost
import com.r3.conclave.host.MailCommand.PostMail
import com.r3.conclave.integrationtests.general.commontest.TestUtils.gramineOnlyTest
import com.r3.conclave.mail.PostOffice
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.nio.file.Paths
import kotlin.io.path.fileSize
import kotlin.io.path.readBytes

class PytorchEnclaveTest {
    companion object {
        @JvmStatic
        @BeforeAll
        fun check() {
            gramineOnlyTest()
        }
    }

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
    fun `run mnist rnn pytorch example` () {
        val clientPostOffice = enclaveHost.enclaveInstanceInfo.createPostOffice()
        val mailRequest = prepareMailWithDataBundle(clientPostOffice)
        enclaveHost.deliverMail(mailRequest, null)
        val mailResponse = postedMail.single().encryptedBytes
        // We do not really care of the accuracy of the result, we just want the Pytorch enclave to run correctly.
        val lastLossResult = clientPostOffice.decryptMail(mailResponse).bodyAsBytes
        assertThat(lastLossResult).isEqualTo("2.2989193603515625".toByteArray())
    }

    private fun prepareMailWithDataBundle(clientPostOffice: PostOffice): ByteArray {
        val pytorchModel = Paths.get(this::class.java.getResource("/bundle.zip")!!.file)

        val body = writeData {
            writeByte(1)
            writeInt(pytorchModel.fileSize().toInt())
            write(pytorchModel.readBytes())
        }
        return clientPostOffice.encryptMail(body)
    }

    private inline fun writeData(block: DataOutputStream.() -> Unit): ByteArray {
        val baos = ByteArrayOutputStream()
        val dos = DataOutputStream(baos)
        block(dos)
        return baos.toByteArray()
    }
}
