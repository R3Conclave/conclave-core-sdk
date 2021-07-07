package com.r3.conclave.integrationtests.general.common

import com.r3.conclave.common.internal.PlaintextAndEnvelope
import com.r3.conclave.enclave.Enclave
import com.r3.conclave.enclave.internal.EnclaveEnvironment

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

    fun runSealUnsealFromBytes(bytes: ByteArray): ByteArray? {
        val command = bytes[0].toInt()

        return when (command) {
            1 -> {
                val unsealedData = TestHelper.parseInputBytes(bytes)
                sealData(unsealedData)
            }
            2 -> {
                val sealedData = bytes.sliceArray(1 until bytes.size)
                var plaintextAndEnvelope = unsealData(sealedData)
                TestHelper.preparePlainTextAndEnvelope(plaintextAndEnvelope)
            }
            else -> "".toByteArray()
        }
    }
}

