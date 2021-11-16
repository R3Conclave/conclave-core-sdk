package com.r3.conclave.integrationtests.filesystem.db.enclave

import com.r3.conclave.common.EnclaveConstraint
import com.r3.conclave.common.EnclaveSecurityInfo
import com.r3.conclave.common.kds.MasterKeyType
import com.r3.conclave.enclave.kds.KDSConfiguration
import com.r3.conclave.enclave.kds.KDSKeySpecification
import com.r3.conclave.enclave.kds.PolicyConstraint
import com.r3.conclave.integrationtests.general.commonenclave.AbstractTestActionEnclave

class DatabaseEnclave  : AbstractTestActionEnclave() {
    override fun getKdsConfig(): KDSConfiguration {
        val policyConstraint = PolicyConstraint().useOwnCodeSignerAndProductID()
        policyConstraint.enclaveConstraint.minSecurityLevel = EnclaveSecurityInfo.Summary.INSECURE
        val kdsEnclaveConstraint = EnclaveConstraint.parse("S:4924CA3A9C8241A3C0AA1A24A407AA86401D2B79FA9FF84932DA798A942166D4 PROD:1 SEC:INSECURE")
        return KDSConfiguration(KDSKeySpecification(MasterKeyType.DEBUG, policyConstraint), kdsEnclaveConstraint)
    }
}
