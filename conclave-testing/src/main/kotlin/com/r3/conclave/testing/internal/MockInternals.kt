package com.r3.conclave.testing.internal

import com.r3.conclave.common.internal.handler.ThrowingErrorHandler
import com.r3.conclave.enclave.Enclave
import com.r3.conclave.host.internal.initHost
import com.r3.conclave.testing.MockHost

object MockInternals {
    fun <T : Enclave> createMock(enclaveClass: Class<T>, isvProdId: Int, isvSvn: Int): MockHost<T> {
        val enclave = enclaveClass.getDeclaredConstructor().apply { isAccessible = true }.newInstance()
        val handle = MockEnclaveHandle(enclave, isvProdId, isvSvn, ThrowingErrorHandler())
        val mockHost = MockHost.create(enclave)
        initHost(mockHost, handle)
        return mockHost
    }
}
