package com.r3.conclave.host.internal

import com.r3.conclave.common.EnclaveMode
import com.r3.conclave.core.common.ThrowingErrorHandler
import com.r3.conclave.core.host.EnclaveLoadMode
import com.r3.conclave.core.host.NativeHostApi
import com.r3.conclave.host.EnclaveHost
import java.nio.file.Path

fun createHost(enclaveFile: Path, enclaveClassName: String, mode: EnclaveMode, tempFile: Boolean): EnclaveHost {
    // TODO NativeHostApi needs to be moved to conclave-host to avoid this mapping.
    val loadMode = when (mode) {
        EnclaveMode.RELEASE -> EnclaveLoadMode.RELEASE
        EnclaveMode.DEBUG -> EnclaveLoadMode.DEBUG
        EnclaveMode.SIMULATION -> EnclaveLoadMode.SIMULATION
    }
    val handle = NativeHostApi(loadMode).createEnclave(ThrowingErrorHandler(), enclaveFile.toFile(), enclaveClassName)
    return EnclaveHost.create(mode, handle, if (tempFile) enclaveFile else null)
}
