package com.r3.conclave.integrationtests.tribuo.client

import com.r3.conclave.client.EnclaveConstraint
import com.r3.conclave.common.EnclaveInstanceInfo

/**
 * This class is responsible for checking the [EnclaveInstanceInfo] for debug and simulation modes.
 */
open class EnclaveInstanceInfoChecker {
    /**
     * Check the [EnclaveInstanceInfo] against the [EnclaveConstraint].
     * @param enclaveInstanceInfo [EnclaveInstanceInfo] to verify.
     */
    open fun check(enclaveInstanceInfo: EnclaveInstanceInfo) {
        EnclaveConstraint.parse("S:4924CA3A9C8241A3C0AA1A24A407AA86401D2B79FA9FF84932DA798A942166D4 PROD:1 SEC:INSECURE").check(enclaveInstanceInfo)
    }
}

/**
 * This class is responsible for checking the [EnclaveInstanceInfo] for mock mode.
 */
class MockEnclaveInstanceInfoChecker: EnclaveInstanceInfoChecker() {
    override fun check(enclaveInstanceInfo: EnclaveInstanceInfo) {
        EnclaveConstraint.parse("S:0000000000000000000000000000000000000000000000000000000000000000 PROD:1 SEC:INSECURE").check(enclaveInstanceInfo)
    }
}