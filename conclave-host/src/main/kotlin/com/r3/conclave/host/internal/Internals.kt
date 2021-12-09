package com.r3.conclave.host.internal

import com.r3.conclave.common.EnclaveMode
import com.r3.conclave.common.MockConfiguration
import com.r3.conclave.host.EnclaveHost
import java.net.URL
import java.util.*

/**
 * [EnclaveHost.internalCreateNative] is internal and so isn't visible to the rest of the codebase. This exists as a
 * public wrapper so that the rest of the codebase has access to it.
 */
@Suppress("unused")  // Used in integration tests
fun createNativeHost(enclaveMode: EnclaveMode, enclaveFileUrl: URL, enclaveClassName: String): EnclaveHost {
    return EnclaveHost.internalCreateNative(enclaveMode, enclaveFileUrl, enclaveClassName)
}

/**
 * [EnclaveHost.internalCreateMock] is internal and so isn't visible to the rest of the codebase. This exists as a
 * public wrapper so that the rest of the codebase has access to it.
 */
fun createMockHost(
    enclaveClass: Class<*>,
    mockConfiguration: MockConfiguration? = null,
    enclavePropertiesOverride: Properties? = null
): EnclaveHost {
    return EnclaveHost.internalCreateMock(enclaveClass, mockConfiguration, enclavePropertiesOverride)
}
