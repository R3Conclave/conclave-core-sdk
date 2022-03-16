package com.r3.conclave.internaltesting.kds.api.request

import com.r3.conclave.common.kds.MasterKeyType
import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoBuf

@Serializable
data class KeySpec(val name: String, val masterKeyType: MasterKeyType, val policyConstraint: String)

@Serializable
sealed class EnclaveRequest {

    companion object {
        fun deserialize(request: ByteArray): EnclaveRequest =
            ProtoBuf.decodeFromByteArray(serializer(), request)
    }

    fun serialize(): ByteArray =
        ProtoBuf.encodeToByteArray(serializer(), this)

    @Serializable
    class PublicKey(
        val keySpec: KeySpec
    ) : EnclaveRequest()

    @Serializable
    class PrivateKey(
        val appAttestationReport: ByteArray,
        val keySpec: KeySpec
    ) : EnclaveRequest()
}