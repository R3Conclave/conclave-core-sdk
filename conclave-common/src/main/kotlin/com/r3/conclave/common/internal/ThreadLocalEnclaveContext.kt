package com.r3.conclave.common.internal

import com.r3.conclave.utilities.internal.EnclaveContext

object ThreadLocalEnclaveContext : EnclaveContext, ThreadLocal<Boolean>() {
    override fun initialValue(): Boolean = false
    override fun isInsideEnclave(): Boolean = get()
}
