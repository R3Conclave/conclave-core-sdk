package com.r3.conclave.integrationtests.general.commonenclave

import com.r3.conclave.common.EnclaveInstanceInfo
import com.r3.conclave.common.internal.Cursor
import com.r3.conclave.common.internal.SgxKeyRequest
import com.r3.conclave.enclave.Enclave
import com.r3.conclave.enclave.internal.EnclaveEnvironment
import com.r3.conclave.enclave.internal.PlaintextAndEnvelope
import com.r3.conclave.integrationtests.general.common.EnclaveContext
import com.r3.conclave.integrationtests.general.common.PlaintextAndAD
import com.r3.conclave.integrationtests.general.common.tasks.EnclaveTestAction
import com.r3.conclave.integrationtests.general.common.tasks.decode
import com.r3.conclave.integrationtests.general.common.tasks.encode
import com.r3.conclave.mail.EnclaveMail
import kotlinx.serialization.PolymorphicSerializer
import java.nio.ByteBuffer
import java.security.PublicKey
import java.security.Signature
import java.util.concurrent.atomic.AtomicReference

abstract class AbstractTestActionEnclave : Enclave() {
    private val env: EnclaveEnvironment by lazy {
        Enclave::class.java.getDeclaredField("env").apply { isAccessible = true }.get(this) as EnclaveEnvironment
    }

    private val stateRef = AtomicReference<Any?>(Nil)
    private object Nil

    private val context = object : EnclaveContext() {
        @Suppress("UNCHECKED_CAST")
        override fun <S> stateAs(): S {
            val state = checkNotNull(stateRef.get()) { "Override createNewState() to define a state object." }
            return state as S
        }
        override fun signer(): Signature {
            return this@AbstractTestActionEnclave.signer()
        }
        override val signatureKey: PublicKey get() {
            return this@AbstractTestActionEnclave.signatureKey
        }
        override fun callUntrustedHost(bytes: ByteArray): ByteArray? {
            return this@AbstractTestActionEnclave.callUntrustedHost(bytes)
        }
        override val enclaveInstanceInfo: EnclaveInstanceInfo get() {
            return getEnclaveInstanceInfo()
        }
        override fun getSecretKey(keyRequest: Cursor<SgxKeyRequest, ByteBuffer>): ByteArray {
            return env.getSecretKey(keyRequest)
        }
        override fun sealData(data: PlaintextAndAD): ByteArray {
            return env.sealData(PlaintextAndEnvelope(data.plaintext, data.authenticatedData))
        }
        override fun unsealData(sealedBlob: ByteArray): PlaintextAndAD {
            return env.unsealData(sealedBlob).let { PlaintextAndAD(it.plaintext, it.authenticatedData) }
        }
    }

    final override fun receiveFromUntrustedHost(bytes: ByteArray): ByteArray = processEncodedAction(bytes, false)

    final override fun receiveMail(id: Long, mail: EnclaveMail, routingHint: String?) {
        val responseBytes = processEncodedAction(mail.bodyAsBytes, true)
        val encryptedResponse = postOffice(mail).encryptMail(responseBytes)
        postMail(encryptedResponse, routingHint)
    }

    private fun processEncodedAction(bytes: ByteArray, isMail: Boolean): ByteArray {
        val action = decode(EnclaveTestAction.serializer(PolymorphicSerializer(Any::class)), bytes)
        updateState(action)
        val result = action.run(context, isMail)
        return encode(action.resultSerializer(), result)
    }

    private fun updateState(action: EnclaveTestAction<*>) {
        if (stateRef.get() == Nil) {
            val newState = action.createNewState()
            stateRef.compareAndSet(Nil, newState)
        }
    }
}
