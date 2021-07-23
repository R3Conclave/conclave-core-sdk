package com.r3.conclave.integrationtests.general.enclave

import com.r3.conclave.common.OpaqueBytes
import com.r3.conclave.common.internal.PlaintextAndEnvelope

object TestHelper {

    fun parseInputBytes(bytes: ByteArray): PlaintextAndEnvelope {
        val plainTextSize = bytes[1].toInt()
        val authenticationSize = bytes[2].toInt()
        val plainText = bytes.sliceArray(3 until plainTextSize + 3)
        val authenticatedData =
            if (authenticationSize != 0) bytes.sliceArray(plainTextSize + 3 until bytes.size) else null
        return PlaintextAndEnvelope(
            OpaqueBytes(plainText),
            if (authenticatedData != null) OpaqueBytes(authenticatedData) else null
        )
    }

    fun preparePlainTextAndEnvelope(plaintextAndEnvelope: PlaintextAndEnvelope): ByteArray {
        val plainTextBytes = plaintextAndEnvelope.plaintext.bytes
        val authenticatedDataBytes = plaintextAndEnvelope.authenticatedData?.bytes
        val sizes = byteArrayOf(
            plainTextBytes.size.toByte(),
            authenticatedDataBytes?.size?.toByte() ?: 0
        )
        val textAndAuthentication = plainTextBytes +
                (authenticatedDataBytes ?: byteArrayOf())
        return sizes + textAndAuthentication
    }
}
