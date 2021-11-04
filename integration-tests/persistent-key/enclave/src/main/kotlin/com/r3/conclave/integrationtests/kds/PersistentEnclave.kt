package com.r3.conclave.integrationtests.kds

import com.r3.conclave.common.EnclaveConstraint
import com.r3.conclave.common.EnclaveSecurityInfo
import com.r3.conclave.common.kds.MasterKeyType
import com.r3.conclave.enclave.Enclave
import com.r3.conclave.enclave.kds.KDSConfiguration
import com.r3.conclave.enclave.kds.KDSKeySpecification
import com.r3.conclave.enclave.kds.PolicyConstraint

class PersistentEnclave : Enclave() {
    companion object {
        private val KDS_CONFIGURATION = kdsConfiguration()

        private fun kdsConfiguration(): KDSConfiguration {
            val policyConstraint = PolicyConstraint()
                .useOwnCodeHash()
                .useOwnCodeSignerAndProductID()
            policyConstraint.enclaveConstraint.minSecurityLevel = EnclaveSecurityInfo.Summary.INSECURE
            val kdsEnclaveConstraint = EnclaveConstraint.parse("S:4924CA3A9C8241A3C0AA1A24A407AA86401D2B79FA9FF84932DA798A942166D4 PROD:1 SEC:INSECURE")
            return KDSConfiguration(KDSKeySpecification(MasterKeyType.DEBUG, policyConstraint), kdsEnclaveConstraint)
        }
    }

    override fun getKdsConfig(): KDSConfiguration {
        return KDS_CONFIGURATION
    }

    override fun onStartup() {
        for (e in persistentMap) {
            println("k: ${e.key}, v: ${String(e.value)}")
        }
    }

    override fun receiveFromUntrustedHost(bytes: ByteArray): ByteArray {
        println("receiveFromUntrustedHost: ${String(bytes)}")
        persistentMap["key"] = bytes
        return byteArrayOf()
    }
}
