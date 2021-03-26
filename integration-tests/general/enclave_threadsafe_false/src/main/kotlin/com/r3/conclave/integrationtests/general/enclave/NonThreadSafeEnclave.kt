package com.r3.conclave.integrationtests.general.enclave

import com.r3.conclave.enclave.Enclave
import com.r3.conclave.integrationtests.general.common.tasks.JvmTestTask
import com.r3.conclave.integrationtests.general.common.tasks.RuntimeContext
import com.r3.conclave.integrationtests.general.common.tasks.format
import java.security.PublicKey
import java.security.Signature

class NonThreadSafeEnclave : Enclave() {
    private val context = object : RuntimeContext() {
        private val keyedValues = mutableMapOf<String,Any?>()

        override fun getSigner(): Signature {
            return signer()
        }

        override fun getPublicKey(): PublicKey {
            return signatureKey
        }

        override fun callHost(bytes: ByteArray): ByteArray? {
            return callUntrustedHost(bytes)
        }
    }

    override fun getThreadSafe(): Boolean {
        return false
    }

    @Override
    override fun receiveFromUntrustedHost(bytes: ByteArray): ByteArray {
        val data = String(bytes)
        val message = format.decodeFromString(JvmTestTask.serializer(), data)
        return message.run(context)
    }
}

