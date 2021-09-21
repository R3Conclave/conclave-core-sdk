package com.r3.conclave.integrationtests.general.threadsafeenclave

import com.r3.conclave.integrationtests.general.commonenclave.AbstractTestActionEnclave

/**
 * This enclave should behave exactly the same as `ThreadSafeEnclave`, including its MRSIGNER value being the same.
 */
class ThreadSafeEnclaveSameSigner : AbstractTestActionEnclave() {
    override fun getThreadSafe(): Boolean = true
}
