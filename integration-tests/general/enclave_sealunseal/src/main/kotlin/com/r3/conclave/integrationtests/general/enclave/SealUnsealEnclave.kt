package com.r3.conclave.integrationtests.general.enclave

import com.r3.conclave.common.OpaqueBytes
import com.r3.conclave.enclave.Enclave
import com.r3.conclave.enclave.internal.EnclaveEnvironment
import com.r3.conclave.common.internal.PlaintextAndEnvelope

abstract class SealUnsealEnclave : Enclave() {

    private val env: EnclaveEnvironment by lazy {
        Enclave::class.java.getDeclaredField("env").apply { isAccessible = true }.get(this) as EnclaveEnvironment
    }

    private fun sealData(unsealedData: PlaintextAndEnvelope): ByteArray {
        return env.sealData(unsealedData)
    }

    private fun unsealData(sealedData: ByteArray): PlaintextAndEnvelope {
        return env.unsealData(sealedData)
    }

    override fun receiveFromUntrustedHost(bytes: ByteArray): ByteArray? {

        return when (bytes[0].toInt()) {
            1 -> {
                val unsealedData = parseInputBytes(bytes)
                sealData(unsealedData)
            }
            2 -> {
                val sealedData = bytes.sliceArray(1 until bytes.size)
                var plaintextAndEnvelope = unsealData(sealedData)
                preparePlainTextAndEnvelope(plaintextAndEnvelope)
            }
            else -> "".toByteArray()
        }
    }

    private fun parseInputBytes(bytes: ByteArray): PlaintextAndEnvelope {
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

    private fun preparePlainTextAndEnvelope(plaintextAndEnvelope: PlaintextAndEnvelope): ByteArray {
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
