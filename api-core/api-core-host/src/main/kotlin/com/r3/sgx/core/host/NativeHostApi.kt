package com.r3.sgx.core.host

import com.r3.conclave.common.internal.ByteCursor
import com.r3.conclave.common.internal.Cursor
import com.r3.conclave.common.internal.SgxMetadata
import com.r3.sgx.core.common.Handler
import com.r3.sgx.core.host.internal.*
import java.io.File

class NativeHostApi(val loadMode: EnclaveLoadMode) {
    val isDebug = when (loadMode) {
        EnclaveLoadMode.RELEASE -> false
        else -> true
    }

    init {
        NativeLoader.loadHostLibraries(loadMode)
    }

    fun <CONNECTION> createEnclave(handler: Handler<CONNECTION>, enclaveFile: File): EnclaveHandle<CONNECTION> {
        val enclaveId = Native.createEnclave(enclaveFile.absolutePath, isDebug)
        val sender = NativeEcallSender(enclaveId, handler)
        return NativeEnclaveHandle(this, enclaveId, sender.connection)
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
