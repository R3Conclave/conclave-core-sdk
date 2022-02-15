package com.r3.conclave.enclave.internal

import com.r3.conclave.common.EnclaveConstraint
import com.r3.conclave.common.kds.MasterKeyType
import com.r3.conclave.enclave.internal.kds.KDSConfiguration
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.util.*

class KDSConfigurationTest {

    companion object {
        private val enclavePropertiesDeprecatedVersion = Properties().apply {
            setProperty("kds.configurationPresent", "true")
            setProperty("kds.kdsEnclaveConstraint", "S:0000000000000000000000000000000000000000000000000000000000000000 PROD:1 SEC:INSECURE")
            setProperty("kds.keySpec.masterKeyType", "debug")
            setProperty("kds.keySpec.policyConstraint.useOwnCodeSignerAndProductID", "true")
            setProperty("kds.keySpec.policyConstraint.useOwnCodeHash", "true")
            setProperty("kds.keySpec.policyConstraint.constraint", "SEC:INSECURE")
        }

        private val enclaveProperties = Properties().apply {
            setProperty("kds.configurationPresent", "true")
            setProperty("kds.kdsEnclaveConstraint", "S:0000000000000000000000000000000000000000000000000000000000000000 PROD:1 SEC:INSECURE")
            setProperty("kds.persistenceKeySpec.masterKeyType", "debug")
            setProperty("kds.persistenceKeySpec.policyConstraint.useOwnCodeSignerAndProductID", "true")
            setProperty("kds.persistenceKeySpec.policyConstraint.useOwnCodeHash", "true")
            setProperty("kds.persistenceKeySpec.policyConstraint.constraint", "SEC:INSECURE")
        }
    }

    @Test
    fun `ensure it is possible to load the deprecated version of kds configuration`() {
        val kdsConfiguration = KDSConfiguration.loadConfiguration(enclavePropertiesDeprecatedVersion)!!
        validateConfiguration(kdsConfiguration)
    }

    @Test
    fun `ensure it is possible to load the kds configuration`() {
        val kdsConfiguration = KDSConfiguration.loadConfiguration(enclaveProperties)!!
        validateConfiguration(kdsConfiguration)
    }

    private fun validateConfiguration(kdsConfiguration: KDSConfiguration) {
        assertThat(kdsConfiguration.kdsEnclaveConstraint.toString()).isEqualTo("S:0000000000000000000000000000000000000000000000000000000000000000 PROD:1 SEC:INSECURE")
        assertThat(kdsConfiguration.kdsKeySpec.masterKeyType).isEqualTo(MasterKeyType.DEBUG)
        assertThat(kdsConfiguration.kdsKeySpec.policyConstraint.enclaveConstraint).isEqualTo(EnclaveConstraint.parse("SEC:INSECURE", false))
        assertThat(kdsConfiguration.kdsKeySpec.policyConstraint.ownCodeHash).isEqualTo(true)
        assertThat(kdsConfiguration.kdsKeySpec.policyConstraint.ownCodeSignerAndProductID).isEqualTo(true)
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

        val kdsConfiguration = KDSConfiguration.loadConfiguration(disabledConfiguration)

        assertThat(kdsConfiguration).isEqualTo(null)
    }
}