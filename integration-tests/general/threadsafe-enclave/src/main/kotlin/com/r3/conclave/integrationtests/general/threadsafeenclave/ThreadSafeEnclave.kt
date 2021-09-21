package com.r3.conclave.integrationtests.general.threadsafeenclave

import com.r3.conclave.enclave.Enclave
import com.r3.conclave.integrationtests.general.commonenclave.AbstractTestActionEnclave

/**
 * Enclave where it's [Enclave.getThreadSafe] flag returns true, i.e. it's meant to allow concurrent execution of
 * `receiveFromUntrustedHost` and `receiveMail`.
 *
 * Also, it's code signer (MRSIGNER) is stable.
 */
class ThreadSafeEnclave : AbstractTestActionEnclave() {
    override fun getThreadSafe(): Boolean = true
}
