package com.r3.conclave.integrationtests.general.common

import com.r3.conclave.common.EnclaveInstanceInfo
import com.r3.conclave.common.internal.Cursor
import com.r3.conclave.common.internal.SgxKeyRequest
import com.r3.conclave.integrationtests.general.common.tasks.EnclaveTestAction
import java.nio.ByteBuffer
import java.security.PublicKey
import java.security.Signature

/**
 * Represents the enclave instance executing a [EnclaveTestAction]. This is typically used to access methods in
 * `Enclave` which are not visible to the action object due to them be having protected visibility.
 */
abstract class EnclaveContext {
    /**
     * Return a custom state object for storing in-memory data across multiple [EnclaveTestAction]s. This requires that
     * [EnclaveTestAction.createNewState] be overridden to return a state object of type [S].
     */
    abstract fun <S> stateAs(): S
    abstract fun signer(): Signature
    abstract val signatureKey: PublicKey
    abstract fun callUntrustedHost(bytes: ByteArray): ByteArray?
    abstract val enclaveInstanceInfo: EnclaveInstanceInfo
    abstract val persistentMap: MutableMap<String, ByteArray>
    abstract fun getSecretKey(keyRequest: Cursor<SgxKeyRequest, ByteBuffer>): ByteArray
    abstract fun sealData(data: PlaintextAndAD): ByteArray
    abstract fun unsealData(sealedBlob: ByteArray): PlaintextAndAD
}
