package com.r3.conclave.host.internal

import com.r3.conclave.common.EnclaveMode
import com.r3.conclave.common.internal.ByteCursor
import com.r3.conclave.common.internal.Cursor
import com.r3.conclave.common.internal.SgxMetadata
import com.r3.conclave.common.internal.handler.Handler
import java.io.File

class NativeHostApi(val mode: EnclaveMode) {
    private val isDebug = when (mode) {
        EnclaveMode.RELEASE -> false
        else -> true
    }

    init {
        NativeLoader.loadHostLibraries(mode)
    }

    fun <CONNECTION> createEnclave(handler: Handler<CONNECTION>, enclaveFile: File, enclaveClassName: String): EnclaveHandle<CONNECTION> {
        val enclaveId = Native.createEnclave(enclaveFile.absolutePath, isDebug)
        return NativeEnclaveHandle(this, enclaveId, handler, enclaveClassName)
    }

    fun destroyEnclave(enclaveId: EnclaveId) {
        Native.destroyEnclave(enclaveId)
    }

    fun readMetadata(enclaveFile: File): ByteCursor<SgxMetadata> {
        val cursor = Cursor.allocate(SgxMetadata)
        Native.getMetadata(enclaveFile.absolutePath, cursor.getBuffer().array())
        return cursor
    }
}
