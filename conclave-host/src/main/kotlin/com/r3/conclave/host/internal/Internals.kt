package com.r3.conclave.host.internal

import com.r3.conclave.common.EnclaveMode
import com.r3.conclave.common.internal.handler.ErrorHandler
import com.r3.conclave.common.internal.handler.ThrowingErrorHandler
import com.r3.conclave.host.EnclaveHost
import java.nio.file.Path

fun createHost(enclaveMode: EnclaveMode, enclaveFile: Path, enclaveClassName: String, tempFile: Boolean): EnclaveHost {
    val enclaveHandle =
        NativeEnclaveHandle(enclaveMode, enclaveFile, tempFile, enclaveClassName, ThrowingErrorHandler())
    return EnclaveHost.__internal_create(enclaveHandle)
}

fun initHost(host: EnclaveHost, enclaveHandle: EnclaveHandle<ErrorHandler.Connection>) {
    EnclaveHost.__internal_init(host, enclaveHandle)
}
