package com.r3.conclave.host.internal

import com.r3.conclave.common.EnclaveMode
import com.r3.conclave.common.MockConfiguration
import com.r3.conclave.host.EnclaveHost
import java.nio.file.Path
import java.util.*

fun createHost(enclaveMode: EnclaveMode, enclaveFile: Path, enclaveClassName: String, tempFile: Boolean): EnclaveHost {
    val enclaveHandle =
        NativeEnclaveHandle(enclaveMode, enclaveFile, tempFile, enclaveClassName, ErrorHandler())
    return EnclaveHost.__internal_create(enclaveHandle)
}

fun createMockHost(enclaveClass: Class<*>, mockConfiguration: MockConfiguration? = null, enclavePropertiesOverride: Properties? = null): EnclaveHost {
    // For mock mode ensure the host can access the enclave constructor. It may have been set as private.
    val constructor = enclaveClass.getDeclaredConstructor()
    constructor.isAccessible = true
    val enclaveHandle = MockEnclaveHandle(constructor.newInstance(), mockConfiguration, enclavePropertiesOverride, ErrorHandler())
    return EnclaveHost.__internal_create(enclaveHandle)
}

fun initHost(host: EnclaveHost, enclaveHandle: EnclaveHandle<ErrorHandler.Connection>) {
    EnclaveHost.__internal_init(host, enclaveHandle)
}
