package com.r3.conclave.enclave

import com.r3.conclave.client.KDSPostOfficeBuilder
import com.r3.conclave.common.EnclaveConstraint
import com.r3.conclave.common.MockConfiguration
import com.r3.conclave.common.kds.KDSKeySpec
import com.r3.conclave.common.kds.MasterKeyType
import com.r3.conclave.internaltesting.kds.KDSServiceMock
import com.r3.conclave.host.EnclaveHost
import com.r3.conclave.host.EnclaveLoadException
import com.r3.conclave.host.internal.createMockHost
import com.r3.conclave.host.kds.KDSConfiguration
import com.r3.conclave.mail.EnclaveMail
import com.r3.conclave.mail.PostOffice
import java.util.*
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.*

class EnclaveKDSKeySpecTest {

    private lateinit var hostEnclave: EnclaveHost
    private lateinit var kdsServiceMock: KDSServiceMock
    private lateinit var kdsPostOffice: PostOffice

    companion object {
        val enclavePropertiesWithoutPersistence = Properties().apply {
            setProperty("kds.configurationPresent", "true")
            setProperty("kds.kdsEnclaveConstraint", "S:0000000000000000000000000000000000000000000000000000000000000000 PROD:1 SEC:INSECURE")
        }
        val enclavePropertiesWithPersistence = Properties().apply {
            setProperty("kds.configurationPresent", "true")
            setProperty("kds.kdsEnclaveConstraint", "S:0000000000000000000000000000000000000000000000000000000000000000 PROD:1 SEC:INSECURE")
            setProperty("kds.persistenceKeySpec.configurationPresent", "true")
            setProperty("kds.persistenceKeySpec.masterKeyType", "debug")
            setProperty("kds.persistenceKeySpec.policyConstraint.useOwnCodeSignerAndProductID", "true")
            setProperty("kds.persistenceKeySpec.policyConstraint.useOwnCodeHash", "true")
            setProperty("kds.persistenceKeySpec.policyConstraint.constraint", "SEC:INSECURE")
        }
    }

    @BeforeEach
    fun init() {
        kdsServiceMock = KDSServiceMock()
        kdsPostOffice = createKDSPostOfficeBuilder()
    }

    @AfterEach
    fun close() {
        kdsServiceMock.close()
        hostEnclave.close()
    }

    @Test
    fun `ensure the enclave starts up properly when using a kds persistence key spec`() {
        assertDoesNotThrow {
            initEnclaveHost(enclavePropertiesWithPersistence)
        }
    }

    @Test
    fun `ensure the enclave validates the name in the key specification for the kds persistence key spec`() {
        kdsServiceMock.addRequestModifier(KDSServiceMock.PrivateKeyRequestModifier(name = "Different name for kds persistence key spec"))
        val thrown = assertThrows<EnclaveLoadException> {
            initEnclaveHost(enclavePropertiesWithPersistence)
        }
        assertThat(thrown.cause?.message).isEqualTo("KDS response was generated using a different name from the one configured in the enclave.")
    }

    @Test
    fun `ensure the enclave validates the master key type in the key specification for the kds persistence key spec`() {
        kdsServiceMock.addRequestModifier(KDSServiceMock.PrivateKeyRequestModifier(masterKeyType = MasterKeyType.RELEASE))
        val thrown = assertThrows<EnclaveLoadException> {
            initEnclaveHost(enclavePropertiesWithPersistence)
        }
        assertThat(thrown.cause?.message).isEqualTo("KDS response was generated using a different master key type from the one configured in the enclave.")
    }

    @Test
    fun `ensure the enclave validates the policy constraint in the key specification for the kds persistence key spec`() {
        kdsServiceMock.addRequestModifier(KDSServiceMock.PrivateKeyRequestModifier(policyConstraint = "S:0000000000000000000000000000000000000000000000000000000000000001 PROD:1 SEC:INSECURE"))
        val thrown = assertThrows<EnclaveLoadException> {
            initEnclaveHost(enclavePropertiesWithPersistence)
        }
        assertThat(thrown.cause?.message).isEqualTo("KDS response was generated using a different policy constraint from the one configured in the enclave.")
    }

    @Test
    fun `ensure the enclave mail is processed successfully when using KDS post office`() {
        initEnclaveHost(enclavePropertiesWithoutPersistence)
        assertDoesNotThrow {
            sendMailToEnclave()
        }
    }

    @Test
    fun `ensure the enclave validates the name in the key specification for mail`() {
        kdsServiceMock.addRequestModifier(KDSServiceMock.PrivateKeyRequestModifier(name = "Different name for mail kds key spec"))
        initEnclaveHost(enclavePropertiesWithoutPersistence)
        val thrown = assertThrows<IllegalArgumentException> {
            sendMailToEnclave()
        }
        assertThat(thrown.message).isEqualTo("KDS response was generated using a different name from the one configured in the enclave.")
    }

    @Test
    fun `ensure the enclave validates the master key type in the key specification for mail`() {
        initEnclaveHost(enclavePropertiesWithoutPersistence)
        kdsServiceMock.addRequestModifier(KDSServiceMock.PrivateKeyRequestModifier(masterKeyType = MasterKeyType.RELEASE))
        val thrown = assertThrows<IllegalArgumentException> {
            sendMailToEnclave()
        }
        assertThat(thrown.message).isEqualTo("KDS response was generated using a different master key type from the one configured in the enclave.")
    }

    @Test
    fun `ensure the enclave validates the policy constraint in the key specification for mail`() {
        initEnclaveHost(enclavePropertiesWithoutPersistence)
        kdsServiceMock.addRequestModifier(KDSServiceMock.PrivateKeyRequestModifier(policyConstraint = "S:0000000000000000000000000000000000000000000000000000000000000001 PROD:1 SEC:INSECURE"))
        val thrown = assertThrows<IllegalArgumentException> {
            sendMailToEnclave()
        }
        assertThat(thrown.message).isEqualTo("KDS response was generated using a different policy constraint from the one configured in the enclave.")
    }

    private fun initEnclaveHost(enclaveProperties: Properties) {
        hostEnclave = createMockHost(NoopEnclave::class.java, MockConfiguration(), enclaveProperties)
        hostEnclave.start(null, null, null, KDSConfiguration(kdsServiceMock.hostUrl.toString())) {}
    }

    private fun createKDSPostOfficeBuilder(): PostOffice {
        val policyConstraint = "S:0000000000000000000000000000000000000000000000000000000000000000 PROD:1 SEC:INSECURE"
        val kdsEnclaveConstraint = EnclaveConstraint.parse("S:0000000000000000000000000000000000000000000000000000000000000000 PROD:1 SEC:INSECURE")
        val kdsSpec = KDSKeySpec("mySpec", MasterKeyType.DEBUG, policyConstraint)
        return KDSPostOfficeBuilder.fromUrl(kdsServiceMock.hostUrl, kdsSpec, kdsEnclaveConstraint).build()
    }

    private fun sendMailToEnclave() {
        val encryptedMail: ByteArray = kdsPostOffice.encryptMail(byteArrayOf())
        hostEnclave.deliverMail(encryptedMail, null) { fromEnclave -> fromEnclave }
    }
}

private class NoopEnclave : Enclave() {
    override fun receiveMail(mail: EnclaveMail, routingHint: String?) = Unit
}