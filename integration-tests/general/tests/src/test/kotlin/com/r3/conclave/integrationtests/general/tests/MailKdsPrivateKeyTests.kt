package com.r3.conclave.integrationtests.general.tests

import com.r3.conclave.client.KDSPostOfficeBuilder
import com.r3.conclave.client.internal.kds.KDSPublicKeyRequest
import com.r3.conclave.common.EnclaveException
import com.r3.conclave.common.internal.kds.KDSUtils
import com.r3.conclave.common.kds.KDSKeySpec
import com.r3.conclave.common.kds.MasterKeyType
import com.r3.conclave.host.EnclaveHost
import com.r3.conclave.host.MailCommand
import com.r3.conclave.host.kds.KDSConfiguration
import com.r3.conclave.integrationtests.general.common.tasks.Echo
import com.r3.conclave.integrationtests.general.common.tasks.EnclaveTestAction
import com.r3.conclave.integrationtests.general.common.tasks.decode
import com.r3.conclave.integrationtests.general.common.tasks.encode
import com.r3.conclave.integrationtests.general.commontest.AbstractEnclaveActionTest
import com.r3.conclave.integrationtests.general.commontest.TestKds.testKdsPort
import com.r3.conclave.mail.Curve25519PublicKey
import com.r3.conclave.mail.EnclaveMail
import com.r3.conclave.mail.MailDecryptionException
import com.r3.conclave.mail.PostOffice
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.nio.file.Path
import java.time.Duration
import java.util.*
import javax.crypto.AEADBadTagException

class MailKdsPrivateKeyTests {
    companion object {
        lateinit var KDS_HOST: String
        lateinit var KDS_URL: URL
        val echoAction = Echo("abc".toByteArray())

        @TempDir
        @JvmField
        var fileSystemFileTempDir: Path? = null

        @BeforeAll
        @JvmStatic
        fun start() {
            val port = testKdsPort
            KDS_HOST = "http://localhost:$port"
            KDS_URL = URL(KDS_HOST)
        }
    }

    @Test
    fun `sending mail to enclave with URL KDS post office gives back a valid result`() {
        val policyConstraint = "S:DF229F35094D9AC237EF23287156F278902579BCD486FA28FA66E87BB1ADBAD2 PROD:1 SEC:INSECURE"
        val kdsSpec = KDSKeySpec("mySpec", MasterKeyType.DEBUG, policyConstraint)
        val postOffice: PostOffice = KDSPostOfficeBuilder.fromUrl(KDS_URL, kdsSpec).build()

        val responseForClient = sendMailWithConstraint(postOffice)
        val result = decode(echoAction.resultSerializer(), responseForClient.bodyAsBytes)
        assertThat(result).isEqualTo("abc".encodeToByteArray())
    }

    @Test
    fun `sending mail to enclave with URL KDS post office with wrong policy constraint throws an exception`() {
        val policyConstraint = "S:DF229F35094D9AC237EF23287156F278902579BCD486FA28FA66E87BB1ADBAD2 PROD:2 SEC:INSECURE"
        val kdsSpec = KDSKeySpec("mySpec", MasterKeyType.DEBUG, policyConstraint)
        val postOffice: PostOffice = KDSPostOfficeBuilder.fromUrl(KDS_URL, kdsSpec).build()

        assertThatThrownBy { sendMailWithConstraint(postOffice) }
            .isInstanceOf(EnclaveException::class.java)
            .hasCauseExactlyInstanceOf(IOException::class.java)
            .cause.hasMessageContaining("The application enclave does not meet the required key policy")
    }

    @Test
    fun `sending mail to enclave with public key manually retrieved from KDS gives back a valid result`() {
        val policyConstraint = "S:DF229F35094D9AC237EF23287156F278902579BCD486FA28FA66E87BB1ADBAD2 PROD:1 SEC:INSECURE"
        val kdsSpec = KDSKeySpec("mySpec", MasterKeyType.DEBUG, policyConstraint)
        val publicKeyString = "bQyMHspHyK1QDUpqPpKrSa0jAd2USmS3tydojhdXOFA="
        val publicKey = Curve25519PublicKey(Base64.getDecoder().decode(publicKeyString))
        val postOffice: PostOffice = KDSPostOfficeBuilder.using(publicKey, kdsSpec).build()
        val responseForClient = sendMailWithConstraint(postOffice)
        val result = decode(echoAction.resultSerializer(), responseForClient.bodyAsBytes)
        assertThat(result).isEqualTo("abc".toByteArray())
    }

    @Test
    fun `sending mail to enclave with public key KDS post office but wrong policy constraint throws an exception`() {
        val policyConstraint = "S:DF229F35094D9AC237EF23287156F278902579BCD486FA28FA66E87BB1ADBAD2 PROD:2 SEC:INSECURE"
        val kdsSpec = KDSKeySpec("mySpec", MasterKeyType.DEBUG, policyConstraint)
        val publicKeyString = "bQyMHspHyK1QDUpqPpKrSa0jAd2USmS3tydojhdXOFA="
        val publicKey = Curve25519PublicKey(Base64.getDecoder().decode(publicKeyString))
        val postOffice: PostOffice = KDSPostOfficeBuilder.using(publicKey, kdsSpec).build()

        assertThatThrownBy { sendMailWithConstraint(postOffice) }
            .isInstanceOf(EnclaveException::class.java)
            .hasCauseExactlyInstanceOf(IOException::class.java)
            .cause.hasMessageContaining("The application enclave does not meet the required key policy")
    }

    @Test
    fun `sending mail to enclave with public key KDS post office but wrong policy constraint name throws an exception`() {
        val policyConstraint = "S:DF229F35094D9AC237EF23287156F278902579BCD486FA28FA66E87BB1ADBAD2 PROD:1 SEC:INSECURE"
        val kdsSpec = KDSKeySpec("myNewSpec", MasterKeyType.DEBUG, policyConstraint)
        val publicKeyString = "bQyMHspHyK1QDUpqPpKrSa0jAd2USmS3tydojhdXOFA="
        val publicKey = Curve25519PublicKey(Base64.getDecoder().decode(publicKeyString))
        val postOffice: PostOffice = KDSPostOfficeBuilder.using(publicKey, kdsSpec).build()

        assertThatThrownBy { sendMailWithConstraint(postOffice) }
            .isInstanceOf(MailDecryptionException::class.java)
            .hasCauseExactlyInstanceOf(AEADBadTagException::class.java)
            .hasMessageContaining("The mail could not be decrypted due to either data corruption or key mismatch")
            .cause.hasMessageContaining("Tag mismatch!")
    }


    @Test
    fun `sending mail to enclave with input stream post office gives back a valid result`() {
        val policyConstraint = "S:DF229F35094D9AC237EF23287156F278902579BCD486FA28FA66E87BB1ADBAD2 PROD:1 SEC:INSECURE"
        val kdsSpec = KDSKeySpec("mySpec", MasterKeyType.DEBUG, policyConstraint)
        val kdsPublicKeyResponseStream = requestKdsPublicKey(kdsSpec)
        val postOffice: PostOffice = KDSPostOfficeBuilder.fromInputStream(kdsPublicKeyResponseStream, kdsSpec).build()
        val responseForClient = sendMailWithConstraint(postOffice)
        val result = decode(echoAction.resultSerializer(), responseForClient.bodyAsBytes)
        assertThat(result).isEqualTo("abc".encodeToByteArray())
    }

    @Test
    fun `sending mail to enclave with input stream post office with wrong policy constraint throws an exception`() {
        val policyConstraint = "S:DF229F35094D9AC237EF23287156F278902579BCD486FA28FA66E87BB1ADBAD2 PROD:2 SEC:INSECURE"
        val kdsSpec = KDSKeySpec("mySpec", MasterKeyType.DEBUG, policyConstraint)
        val kdsPublicKeyResponseStream = requestKdsPublicKey(kdsSpec)
        val postOffice: PostOffice = KDSPostOfficeBuilder.fromInputStream(kdsPublicKeyResponseStream, kdsSpec).build()

        assertThatThrownBy { sendMailWithConstraint(postOffice) }
            .isInstanceOf(EnclaveException::class.java)
            .hasCauseExactlyInstanceOf(IOException::class.java)
            .cause.hasMessageContaining("The application enclave does not meet the required key policy")
    }

    private fun requestKdsPublicKey(keySpec: KDSKeySpec): InputStream {
        val mapper = KDSUtils.getJsonMapper()
        val publicKeyRequest = KDSPublicKeyRequest(keySpec.name, keySpec.masterKeyType, keySpec.policyConstraint)
        val publicUrl = URL("${KDS_URL}/public")
        // TODO Replace this with java.net.http.HttpClient
        val con: HttpURLConnection = publicUrl.openConnection() as HttpURLConnection
        con.connectTimeout = Duration.ofSeconds(10).toMillis().toInt()
        con.readTimeout = Duration.ofSeconds(10).toMillis().toInt()
        con.requestMethod = "POST"
        con.setRequestProperty("Content-Type", "application/json; utf-8")
        con.setRequestProperty("API-VERSION", "1")
        con.doOutput = true

        con.outputStream.use {
            mapper.writeValue(it, publicKeyRequest)
        }
        return con.inputStream
    }

    private fun sendMailWithConstraint(postOffice: PostOffice): EnclaveMail {
        val capturedCommands = ArrayList<MailCommand>()
        val enclaveClassName = "com.r3.conclave.integrationtests.general.threadsafeenclave.ThreadSafeEnclave"
        val hostEnclave = EnclaveHost.load(enclaveClassName)
        val attestationParameters =
            if (hostEnclave.enclaveMode.isHardware) AbstractEnclaveActionTest.getHardwareAttestationParams() else null

        hostEnclave.start(attestationParameters, null, null, KDSConfiguration(KDS_HOST)) { commands ->
            capturedCommands += commands
        }
        val encodedAction = encode(EnclaveTestAction.serializer(echoAction.resultSerializer()), echoAction)
        val encryptedMail: ByteArray = postOffice.encryptMail(encodedAction)

        hostEnclave.deliverMail(encryptedMail, null) { fromEnclave ->
            fromEnclave
        }
        val postMail = capturedCommands.filterIsInstance<MailCommand.PostMail>().single()
        return postOffice.decryptMail(postMail.encryptedBytes)
    }
}
