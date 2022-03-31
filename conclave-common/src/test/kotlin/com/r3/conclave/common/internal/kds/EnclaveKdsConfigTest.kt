package com.r3.conclave.common.internal.kds

import com.r3.conclave.common.kds.MasterKeyType
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.util.*

class EnclaveKdsConfigTest {
    companion object {
        private val enclavePropertiesDeprecatedVersion = Properties().apply {
            setProperty("kds.configurationPresent", "true")
            setProperty("kds.kdsEnclaveConstraint", "S:0000000000000000000000000000000000000000000000000000000000000000 PROD:1 SEC:INSECURE")
            setProperty("kds.persistenceKeySpec.configurationPresent", "true")
            setProperty("kds.keySpec.masterKeyType", "debug")
            setProperty("kds.keySpec.policyConstraint.useOwnCodeSignerAndProductID", "true")
            setProperty("kds.keySpec.policyConstraint.useOwnCodeHash", "true")
            setProperty("kds.keySpec.policyConstraint.constraint", "SEC:INSECURE")
        }

        private val enclaveProperties = Properties().apply {
            setProperty("kds.configurationPresent", "true")
            setProperty("kds.kdsEnclaveConstraint", "S:0000000000000000000000000000000000000000000000000000000000000000 PROD:1 SEC:INSECURE")
            setProperty("kds.persistenceKeySpec.configurationPresent", "true")
            setProperty("kds.persistenceKeySpec.masterKeyType", "debug")
            setProperty("kds.persistenceKeySpec.policyConstraint.useOwnCodeSignerAndProductID", "true")
            setProperty("kds.persistenceKeySpec.policyConstraint.useOwnCodeHash", "true")
            setProperty("kds.persistenceKeySpec.policyConstraint.constraint", "SEC:INSECURE")
        }
    }

    @Test
    fun `ensure it is possible to load the deprecated version of kds configuration`() {
        val kdsConfiguration = EnclaveKdsConfig.loadConfiguration(enclavePropertiesDeprecatedVersion)!!
        validateConfiguration(kdsConfiguration)
    }

    @Test
    fun `ensure it is possible to load the kds configuration`() {
        val kdsConfiguration = EnclaveKdsConfig.loadConfiguration(enclaveProperties)!!
        validateConfiguration(kdsConfiguration)
    }

    @Test
    fun `useOwnCodeSignerAndProductID and useOwnCodeHash default to false`() {
        val properties = Properties().apply {
            setProperty("kds.configurationPresent", "true")
            setProperty("kds.kdsEnclaveConstraint", "S:0000000000000000000000000000000000000000000000000000000000000000 PROD:1 SEC:INSECURE")
            setProperty("kds.persistenceKeySpec.configurationPresent", "true")
            setProperty("kds.persistenceKeySpec.masterKeyType", "debug")
            setProperty("kds.persistenceKeySpec.policyConstraint.constraint", "SEC:INSECURE")
        }

        val persistenceKeySpec = EnclaveKdsConfig.loadConfiguration(properties)!!.persistenceKeySpec!!
        assertThat(persistenceKeySpec.policyConstraint.useOwnCodeSignerAndProductID).isFalse
        assertThat(persistenceKeySpec.policyConstraint.useOwnCodeHash).isFalse
    }

    @Test
    fun `disabled kds configuration`() {
        val disabledConfiguration = Properties().apply {
            setProperty("kds.configurationPresent", "false")
            setProperty("kds.kdsEnclaveConstraint", "S:0000000000000000000000000000000000000000000000000000000000000000 PROD:1 SEC:INSECURE")
            setProperty("kds.persistenceKeySpec.masterKeyType", "debug")
            setProperty("kds.persistenceKeySpec.policyConstraint.useOwnCodeSignerAndProductID", "true")
            setProperty("kds.persistenceKeySpec.policyConstraint.useOwnCodeHash", "true")
            setProperty("kds.persistenceKeySpec.policyConstraint.constraint", "SEC:INSECURE")
        }

        val kdsConfiguration = EnclaveKdsConfig.loadConfiguration(disabledConfiguration)
        assertThat(kdsConfiguration).isNull()
    }

    private fun validateConfiguration(kdsConfiguration: EnclaveKdsConfig) {
        assertThat(kdsConfiguration.kdsEnclaveConstraint.toString()).isEqualTo("S:0000000000000000000000000000000000000000000000000000000000000000 PROD:1 SEC:INSECURE")
        assertThat(kdsConfiguration.persistenceKeySpec!!.masterKeyType).isEqualTo(MasterKeyType.DEBUG)
        assertThat(kdsConfiguration.persistenceKeySpec!!.policyConstraint.constraint).isEqualTo("SEC:INSECURE")
        assertThat(kdsConfiguration.persistenceKeySpec!!.policyConstraint.useOwnCodeHash).isEqualTo(true)
        assertThat(kdsConfiguration.persistenceKeySpec!!.policyConstraint.useOwnCodeSignerAndProductID).isEqualTo(true)
    }
}
