package com.r3.conclave.host.internal

import com.r3.conclave.common.EnclaveMode
import com.r3.conclave.common.internal.handler.ThrowingErrorHandler
import com.r3.conclave.host.EnclaveHost
import java.nio.file.Path

fun createHost(enclaveFile: Path, enclaveClassName: String, mode: EnclaveMode, tempFile: Boolean): EnclaveHost {
    val handle = NativeHostApi(mode).createEnclave(ThrowingErrorHandler(), enclaveFile.toFile(), enclaveClassName)
    return EnclaveHost.create(mode, handle, if (tempFile) enclaveFile else null)
}
