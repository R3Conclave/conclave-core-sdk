package com.r3.conclave.integrationtests.general.tests

import com.r3.conclave.common.OpaqueBytes
import com.r3.conclave.common.internal.*
import com.r3.conclave.enclave.Enclave
import com.r3.conclave.host.EnclaveHost
import java.nio.ByteBuffer
import kotlin.reflect.KProperty0

typealias ByteCursor<T> = Cursor<T, ByteBuffer>

/**
 * A container of [SecretKeyAction]s which together make a specification for creating enclave secret keys.
 */
data class SecretKeySpec(val actions: List<SecretKeyAction>) {
    /**
     * Query for the secret key, using the given lookup function to provide the [EnclaveHost] for the enclave class.
     */
    fun querySecretKey(hostLookup: (EnclaveSpec) -> EnclaveHost): Result {
        val enclaveClass = actions.filterIsInstance<EnclaveClass>().single().enclaveClass
        val isvProdId = actions.filterIsInstance<EnclaveIsvProdId>().single().isvProdId
        val isvSvn = actions.filterIsInstance<EnclaveIsvSvn>().single().isvSvn
        val host = hostLookup(EnclaveSpec(enclaveClass, isvProdId, isvSvn))
        val keyRequest = Cursor.allocate(SgxKeyRequest.INSTANCE)
        for (action in actions) {
            (action as? SetKeyRequestField)?.apply(keyRequest, host.enclaveInstanceInfo as EnclaveInstanceInfoImpl)
        }

        val secretKey = try {
            host.callEnclave(keyRequest.buffer.array())
        } catch (e: Exception) {
            val message = e.message!!
            if (message.startsWith("SGX_ERROR_")) {
                return Result.Error(message)
            } else {
                throw e
            }
        }
        return Result.Key(OpaqueBytes(secretKey!!))
    }

    override fun toString(): String = "SecretKeySpec($actions)"
}

/**
 * Marker interface for all actions that can be part of a [SecretKeySpec] for querying enclave secret keys.
 */
interface SecretKeyAction

/**
 * Specify the class of the enclave to query the secret key from.
 */
data class EnclaveClass(val enclaveClass: Class<out Enclave>) : SecretKeyAction {
    override fun toString(): String = "EnclaveClass=${enclaveClass.simpleName}"
}

/**
 * Specify the product ID the enclave should have.
 */
data class EnclaveIsvProdId(val isvProdId: Int) : SecretKeyAction {
    override fun toString(): String = "EnclaveIsvProdId=$isvProdId"
}

/**
 * Specify the ISV SVN the enclave should have.
 *
 * Note, this is different to [IsvSvnFieldDelta], which specifies what the [SgxKeyRequest.isvSvn] value should be relative
 * to this one.
 */
data class EnclaveIsvSvn(val isvSvn: Int) : SecretKeyAction {
    override fun toString(): String = "EnclaveIsvSvn=$isvSvn"
}

data class EnclaveSpec(val enclaveClass: Class<out Enclave>, val isvProdId: Int, val isvSvn: Int)

/**
 * A [SecretKeyAction] which updates a particular field of a provided [SgxKeyRequest]. This key request object, once it's
 * fully initialised, is sent to the enclave for querying the secret key.
 */
interface SetKeyRequestField : SecretKeyAction {
    fun apply(keyRequest: ByteCursor<SgxKeyRequest>, enclaveInstanceInfo: EnclaveInstanceInfoImpl)
}

data class KeyNameField(val keyName: Int) : SetKeyRequestField {
    init {
        require(KeyName.INSTANCE.values.containsValue(keyName))
    }

    override fun apply(keyRequest: ByteCursor<SgxKeyRequest>, enclaveInstanceInfo: EnclaveInstanceInfoImpl) {
        keyRequest[SgxKeyRequest.keyName] = keyName
    }

    override fun toString(): String = "keyName=${KeyName.INSTANCE.values.entries.first { it.value == keyName }.key}"
}

data class KeyPolicyField(val keyPolicyFlags: Set<Int>) : SetKeyRequestField {
    init {
        require(keyPolicyFlags.all(KeyPolicy.INSTANCE.values::containsValue))
    }

    override fun apply(keyRequest: ByteCursor<SgxKeyRequest>, enclaveInstanceInfo: EnclaveInstanceInfoImpl) {
        if (keyPolicyFlags.isNotEmpty()) {
            keyRequest[SgxKeyRequest.keyPolicy] = keyPolicyFlags.fold(0, Int::or)
        }
    }

    override fun toString(): String = "keyPolicy=${KeyPolicy.INSTANCE.values.filterValues { it in keyPolicyFlags }.keys}"
}

data class IsvSvnFieldDelta(val isvSvnDelta: Int) : SetKeyRequestField {
    override fun apply(keyRequest: ByteCursor<SgxKeyRequest>, enclaveInstanceInfo: EnclaveInstanceInfoImpl) {
        val currentIsvSvn = enclaveInstanceInfo.enclaveInfo.revocationLevel + 1
        keyRequest[SgxKeyRequest.isvSvn] = currentIsvSvn + isvSvnDelta
    }

    override fun toString(): String = "isvSvn=<current value>${if (isvSvnDelta < 0) isvSvnDelta else "+$isvSvnDelta"}"
}

data class CpuSvnField(val cpuSvn: OpaqueBytes?) : SetKeyRequestField {
    init {
        require(cpuSvn == null || cpuSvn.size == SgxCpuSvn.INSTANCE.size)
    }

    override fun apply(keyRequest: ByteCursor<SgxKeyRequest>, enclaveInstanceInfo: EnclaveInstanceInfoImpl) {
        val currentCpuSvn = enclaveInstanceInfo.securityInfo.cpuSVN
        keyRequest[SgxKeyRequest.cpuSvn] = (cpuSvn ?: currentCpuSvn).buffer()
    }

    override fun toString(): String = "cpuSvn=${cpuSvn ?: "<current value>"}"
}

data class KeyIdField(val keyId: OpaqueBytes) : SetKeyRequestField {
    init {
        require(keyId.size == SgxKeyId.INSTANCE.size)
    }

    override fun apply(keyRequest: ByteCursor<SgxKeyRequest>, enclaveInstanceInfo: EnclaveInstanceInfoImpl) {
        keyRequest[SgxKeyRequest.keyId] = keyId.buffer()
    }

    override fun toString(): String = "keyId=$keyId"
}

/**
 * This doesn't do anything, but is required when creating the cartesian product of the various key request combinations
 * to make sure that there is a [SecretKeySpec] which leaves the given field blank.
 */
data class BlankField(val field: KProperty0<AbstractStruct.Field<SgxKeyRequest, *>>) : SetKeyRequestField {
    override fun apply(keyRequest: ByteCursor<SgxKeyRequest>, enclaveInstanceInfo: EnclaveInstanceInfoImpl) = Unit
    override fun toString(): String = "${field.name}=null"
}

sealed class Result {
    data class Key(val bytes: OpaqueBytes) : Result() {
        override fun toString(): String = "Key($bytes)"
    }

    data class Error(val message: String) : Result() {
        override fun toString(): String = "Error($message)"
    }
}
