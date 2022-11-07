package com.r3.conclave.host.internal

import com.r3.conclave.common.EnclaveMode
import com.r3.conclave.common.MockConfiguration
import com.r3.conclave.common.internal.kds.EnclaveKdsConfig
import com.r3.conclave.host.EnclaveHost
import java.net.URL

/**
 * [EnclaveHost.internalCreateNonMock] is internal and so isn't visible to the rest of the codebase. This exists as a
 * public wrapper so that the rest of the codebase has access to it.
 */
@Suppress("unused")  // Used in integration tests
fun createNonMockHost(scanResult: EnclaveScanner.ScanResult): EnclaveHost {
    return EnclaveHost.internalCreateNonMock(scanResult)
}

/**
 * [EnclaveHost.internalCreateMock] is internal and so isn't visible to the rest of the codebase. This exists as a
 * public wrapper so that the rest of the codebase has access to it.
 */
fun createMockHost(
    enclaveClass: Class<*>,
    mockConfiguration: MockConfiguration? = null,
    enclaveKdsConfig: EnclaveKdsConfig? = null
): EnclaveHost {
    return EnclaveHost.internalCreateMock(enclaveClass, mockConfiguration, enclaveKdsConfig)
}
