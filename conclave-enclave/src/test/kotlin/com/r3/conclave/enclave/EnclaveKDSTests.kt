package com.r3.conclave.enclave

import com.r3.conclave.client.PostOfficeBuilder
import com.r3.conclave.common.MockConfiguration
import com.r3.conclave.common.SHA256Hash
import com.r3.conclave.common.internal.kds.EnclaveKdsConfig
import com.r3.conclave.common.kds.KDSKeySpec
import com.r3.conclave.common.kds.MasterKeyType
import com.r3.conclave.host.EnclaveHost
import com.r3.conclave.host.EnclaveLoadException
import com.r3.conclave.host.internal.createMockHost
import com.r3.conclave.host.kds.KDSConfiguration
import com.r3.conclave.internaltesting.kds.MockKDS
import com.r3.conclave.internaltesting.kds.PrivateKeyRequestModifier
import com.r3.conclave.mail.EnclaveMail
import com.r3.conclave.mail.PostOffice
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.*
import org.junit.jupiter.api.extension.RegisterExtension
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

class EnclaveKDSTests {
    companion object {
        private const val APP_ENCLAVE_CODE_HASH = "7ED0D171B7BE8D5DB0391D786A96BA8004DEF81B27B99283904062E2DB46ED63"
        private const val APP_ENCLAVE_CODE_SIGNER = "9DF32DDABAD154C5C2E0E08732ECA0ADFE32D9BC31732522F61E497849C97BAC"

        private val mockConfig = MockConfiguration().apply {
            codeHash = SHA256Hash.parse(APP_ENCLAVE_CODE_HASH)
            codeSigningKeyHash = SHA256Hash.parse(APP_ENCLAVE_CODE_SIGNER)
            productID = 12
        }
    }

    @RegisterExtension
    private val mockKDS = MockKDS()
    private lateinit var enclaveHost: EnclaveHost
    private lateinit var kdsPostOffice: PostOffice

    private val kdsConfigWithoutPersistence = EnclaveKdsConfig(
        kdsEnclaveConstraint = mockKDS.enclaveConstraint,
        persistenceKeySpec = null
    )

    private val kdsConfigWithPersistence = EnclaveKdsConfig(
        kdsEnclaveConstraint = mockKDS.enclaveConstraint,
        persistenceKeySpec = EnclaveKdsConfig.PersistenceKeySpec(
            masterKeyType = MasterKeyType.DEBUG,
            policyConstraint = EnclaveKdsConfig.PolicyConstraint(
                constraint = "SEC:INSECURE",
                useOwnCodeSignerAndProductID = true
            )
        )
    )

    @BeforeEach
    fun init() {
        // Make sure we test that the enclave is checking regardless of the KDS.
        mockKDS.checkPolicyConstraint = false
        kdsPostOffice = createKdsPostOffice()
    }

    @AfterEach
    fun close() {
        if (::enclaveHost.isInitialized) {
            enclaveHost.close()
        }
    }

    @Test
    fun `'EnclavePersistence' is used for persistence key name`() {
        startEnclave(kdsConfigWithPersistence)
        assertThat(mockKDS.previousPrivateKeyRequest?.name).isEqualTo("EnclavePersistence")
    }

    @Test
    fun `policy constraint is used as is if useOwn properties aren't specified`() {
        val kdsConfig = EnclaveKdsConfig(
            kdsEnclaveConstraint = mockKDS.enclaveConstraint,
            persistenceKeySpec = EnclaveKdsConfig.PersistenceKeySpec(
                masterKeyType = MasterKeyType.DEBUG,
                policyConstraint = EnclaveKdsConfig.PolicyConstraint(
                    constraint = "  C:$APP_ENCLAVE_CODE_HASH SEC:INSECURE  ",
                )
            )
        )

        startEnclave(kdsConfig)
        assertThat(mockKDS.previousPrivateKeyRequest?.policyConstraint).isEqualTo("  C:$APP_ENCLAVE_CODE_HASH SEC:INSECURE  ")
    }

    @Test
    fun `exception is thrown if base policy constraint is incomplete`() {
        val kdsConfig = EnclaveKdsConfig(
            kdsEnclaveConstraint = mockKDS.enclaveConstraint,
            persistenceKeySpec = EnclaveKdsConfig.PersistenceKeySpec(
                masterKeyType = MasterKeyType.DEBUG,
                policyConstraint = EnclaveKdsConfig.PolicyConstraint(constraint = "SEC:INSECURE")
            )
        )

        val thrown = assertThrows<EnclaveLoadException> {
            startEnclave(kdsConfig)
        }
        assertThat(thrown.cause).hasMessageContaining("Invalid policy constraint")
    }

    @Test
    fun `code hash is appended to the end of policy constraint if useOwnCodeHash is specified, even if another code hash is specified in the constraint`() {
        val kdsConfig = EnclaveKdsConfig(
            kdsEnclaveConstraint = mockKDS.enclaveConstraint,
            persistenceKeySpec = EnclaveKdsConfig.PersistenceKeySpec(
                masterKeyType = MasterKeyType.DEBUG,
                policyConstraint = EnclaveKdsConfig.PolicyConstraint(
                    useOwnCodeHash = true,
                    constraint = " C:0000000000000000000000000000000000000000000000000000000000000000 SEC:INSECURE"  // Note leading space
                )
            )
        )

        startEnclave(kdsConfig)
        assertThat(mockKDS.previousPrivateKeyRequest?.policyConstraint)
            .isEqualTo(" C:0000000000000000000000000000000000000000000000000000000000000000 SEC:INSECURE C:$APP_ENCLAVE_CODE_HASH")
    }

    @ParameterizedTest
    @ValueSource(booleans = [false, true])
    fun `useOwnCodeHash is ignored if matching code hash is specified in the policy constraint`(uppercase: Boolean) {
        val appEnclaveCodeHash = if (uppercase) APP_ENCLAVE_CODE_HASH else APP_ENCLAVE_CODE_HASH.lowercase()

        val kdsConfig = EnclaveKdsConfig(
            kdsEnclaveConstraint = mockKDS.enclaveConstraint,
            persistenceKeySpec = EnclaveKdsConfig.PersistenceKeySpec(
                masterKeyType = MasterKeyType.DEBUG,
                policyConstraint = EnclaveKdsConfig.PolicyConstraint(
                    useOwnCodeHash = true,
                    constraint = "C:$appEnclaveCodeHash SEC:INSECURE"
                )
            )
        )

        startEnclave(kdsConfig)
        assertThat(mockKDS.previousPrivateKeyRequest?.policyConstraint).isEqualTo("C:$appEnclaveCodeHash SEC:INSECURE")
    }

    @Test
    fun `code signer and then product ID are appended to the end of policy constraint if useOwnCodeSignerAndProductID is specified`() {
        val kdsConfig = EnclaveKdsConfig(
            kdsEnclaveConstraint = mockKDS.enclaveConstraint,
            persistenceKeySpec = EnclaveKdsConfig.PersistenceKeySpec(
                masterKeyType = MasterKeyType.DEBUG,
                policyConstraint = EnclaveKdsConfig.PolicyConstraint(
                    useOwnCodeSignerAndProductID = true,
                    constraint = " SEC:INSECURE" // Note leading space
                )
            )
        )

        startEnclave(kdsConfig)
        assertThat(mockKDS.previousPrivateKeyRequest?.policyConstraint).isEqualTo(" SEC:INSECURE S:$APP_ENCLAVE_CODE_SIGNER PROD:12")
    }

    @ParameterizedTest
    @ValueSource(booleans = [false, true])
    fun `useOwnCodeSignerAndProductID is ignored if matching coder signer and product ID is specified in the policy constraint`(uppercase: Boolean) {
        val appEnclaveCodeSigner = if (uppercase) APP_ENCLAVE_CODE_SIGNER else APP_ENCLAVE_CODE_SIGNER.lowercase()

        val kdsConfig = EnclaveKdsConfig(
            kdsEnclaveConstraint = mockKDS.enclaveConstraint,
            persistenceKeySpec = EnclaveKdsConfig.PersistenceKeySpec(
                masterKeyType = MasterKeyType.DEBUG,
                policyConstraint = EnclaveKdsConfig.PolicyConstraint(
                    useOwnCodeSignerAndProductID = true,
                    constraint = " S:$appEnclaveCodeSigner SEC:INSECURE PROD:12" // Note leading space
                )
            )
        )

        startEnclave(kdsConfig)
        assertThat(mockKDS.previousPrivateKeyRequest?.policyConstraint).isEqualTo(" S:$appEnclaveCodeSigner SEC:INSECURE PROD:12")
    }

    @Test
    fun `code signer is appended to the end of the policy constraint if useOwnCodeSignerAndProductID is specified along with just the product ID in the policy constraint`() {
        val kdsConfig = EnclaveKdsConfig(
            kdsEnclaveConstraint = mockKDS.enclaveConstraint,
            persistenceKeySpec = EnclaveKdsConfig.PersistenceKeySpec(
                masterKeyType = MasterKeyType.DEBUG,
                policyConstraint = EnclaveKdsConfig.PolicyConstraint(
                    useOwnCodeSignerAndProductID = true,
                    constraint = "PROD:12 SEC:INSECURE"
                )
            )
        )

        startEnclave(kdsConfig)
        assertThat(mockKDS.previousPrivateKeyRequest?.policyConstraint).isEqualTo("PROD:12 SEC:INSECURE S:$APP_ENCLAVE_CODE_SIGNER")
    }

    @Test
    fun `exception is thrown if useOwnCodeSignerAndProductID is specified and the product ID is specified in the policy constraint but it's different'`() {
        val kdsConfig = EnclaveKdsConfig(
            kdsEnclaveConstraint = mockKDS.enclaveConstraint,
            persistenceKeySpec = EnclaveKdsConfig.PersistenceKeySpec(
                masterKeyType = MasterKeyType.DEBUG,
                policyConstraint = EnclaveKdsConfig.PolicyConstraint(
                    useOwnCodeSignerAndProductID = true,
                    constraint = "PROD:13 SEC:INSECURE"
                )
            )
        )

        val thrown = assertThrows<EnclaveLoadException> {
            startEnclave(kdsConfig)
        }

        assertThat(thrown.cause).hasMessageContaining("Cannot apply useOwnCodeSignerAndProductID to the KDS " +
                "persistence policy constraint as PROD:13 is already specified")
    }

    @Test
    fun `product ID is appended to the end of the policy constraint if useOwnCodeSignerAndProductID is specified along with just the code signer in the policy constraint`() {
        val kdsConfig = EnclaveKdsConfig(
            kdsEnclaveConstraint = mockKDS.enclaveConstraint,
            persistenceKeySpec = EnclaveKdsConfig.PersistenceKeySpec(
                masterKeyType = MasterKeyType.DEBUG,
                policyConstraint = EnclaveKdsConfig.PolicyConstraint(
                    useOwnCodeSignerAndProductID = true,
                    constraint = "S:$APP_ENCLAVE_CODE_SIGNER SEC:INSECURE"
                )
            )
        )

        startEnclave(kdsConfig)
        assertThat(mockKDS.previousPrivateKeyRequest?.policyConstraint).isEqualTo("S:$APP_ENCLAVE_CODE_SIGNER SEC:INSECURE PROD:12")
    }

    @Test
    fun `if both useOwnCodeHash and useOwnCodeSignerAndProductID are specified then code hash is appended first`() {
        val kdsConfig = EnclaveKdsConfig(
            kdsEnclaveConstraint = mockKDS.enclaveConstraint,
            persistenceKeySpec = EnclaveKdsConfig.PersistenceKeySpec(
                masterKeyType = MasterKeyType.DEBUG,
                policyConstraint = EnclaveKdsConfig.PolicyConstraint(
                    useOwnCodeHash = true,
                    useOwnCodeSignerAndProductID = true,
                    constraint = "SEC:INSECURE"
                )
            )
        )

        startEnclave(kdsConfig)
        assertThat(mockKDS.previousPrivateKeyRequest?.policyConstraint)
            .isEqualTo("SEC:INSECURE C:$APP_ENCLAVE_CODE_HASH S:$APP_ENCLAVE_CODE_SIGNER PROD:12")
    }

    @Test
    fun `ensure the enclave validates the name in the key specification for the kds persistence key spec`() {
        mockKDS.privateKeyRequestModifier = PrivateKeyRequestModifier(name = "Different name for kds persistence key spec")
        val thrown = assertThrows<EnclaveLoadException> {
            startEnclave(kdsConfigWithPersistence)
        }
        assertThat(thrown.cause).hasMessageContaining("KDS private key response does not match requested key spec.")
    }

    @Test
    fun `ensure the enclave validates the master key type in the key specification for the kds persistence key spec`() {
        mockKDS.privateKeyRequestModifier = PrivateKeyRequestModifier(masterKeyType = MasterKeyType.RELEASE)
        val thrown = assertThrows<EnclaveLoadException> {
            startEnclave(kdsConfigWithPersistence)
        }
        assertThat(thrown.cause).hasMessageContaining("KDS private key response does not match requested key spec.")
    }

    @Test
    fun `ensure the enclave validates the policy constraint in the key specification for the kds persistence key spec`() {
        mockKDS.privateKeyRequestModifier = PrivateKeyRequestModifier(policyConstraint = "S:0000000000000000000000000000000000000000000000000000000000000001 PROD:1 SEC:INSECURE")
        val thrown = assertThrows<EnclaveLoadException> {
            startEnclave(kdsConfigWithPersistence)
        }
        assertThat(thrown.cause).hasMessageContaining("KDS private key response does not match requested key spec.")
    }

    @Test
    fun `ensure the enclave mail is processed successfully when using KDS post office`() {
        startEnclave(kdsConfigWithoutPersistence)
        assertDoesNotThrow {
            sendMailToEnclave()
        }
    }

    @Test
    fun `ensure the enclave validates the name in the key specification for mail`() {
        mockKDS.privateKeyRequestModifier = PrivateKeyRequestModifier(name = "Different name for mail kds key spec")
        startEnclave(kdsConfigWithoutPersistence)
        val thrown = assertThrows<IllegalArgumentException> {
            sendMailToEnclave()
        }
        assertThat(thrown).hasMessageContaining("KDS private key response does not match requested key spec.")
    }

    @Test
    fun `ensure the enclave validates the master key type in the key specification for mail`() {
        startEnclave(kdsConfigWithoutPersistence)
        mockKDS.privateKeyRequestModifier = PrivateKeyRequestModifier(masterKeyType = MasterKeyType.RELEASE)
        val thrown = assertThrows<IllegalArgumentException> {
            sendMailToEnclave()
        }
        assertThat(thrown).hasMessageContaining("KDS private key response does not match requested key spec.")
    }

    @Test
    fun `ensure the enclave validates the policy constraint in the key specification for mail`() {
        startEnclave(kdsConfigWithoutPersistence)
        mockKDS.privateKeyRequestModifier = PrivateKeyRequestModifier(policyConstraint = "S:0000000000000000000000000000000000000000000000000000000000000001 PROD:1 SEC:INSECURE")
        val thrown = assertThrows<IllegalArgumentException> {
            sendMailToEnclave()
        }
        assertThat(thrown).hasMessageContaining("KDS private key response does not match requested key spec.")
    }

    private fun startEnclave(kdsConfig: EnclaveKdsConfig) {
        enclaveHost = createMockHost(NoopEnclave::class.java, mockConfig, kdsConfig)
        enclaveHost.start(null, null, null, KDSConfiguration(mockKDS.url.toString())) {}
    }

    private fun createKdsPostOffice(): PostOffice {
        val policyConstraint = "S:0000000000000000000000000000000000000000000000000000000000000000 PROD:1 SEC:INSECURE"
        val kdsSpec = KDSKeySpec("mySpec", MasterKeyType.DEBUG, policyConstraint)
        return PostOfficeBuilder.usingKDS(mockKDS.url, kdsSpec, mockKDS.enclaveConstraint).build()
    }

    private fun sendMailToEnclave() {
        val encryptedMail: ByteArray = kdsPostOffice.encryptMail(byteArrayOf())
        enclaveHost.deliverMail(encryptedMail, null)
    }

    private class NoopEnclave : Enclave() {
        override fun receiveMail(mail: EnclaveMail, routingHint: String?) = Unit
    }
}
