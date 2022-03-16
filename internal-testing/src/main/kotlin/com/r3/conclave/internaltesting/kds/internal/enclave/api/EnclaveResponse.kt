package com.r3.conclave.internaltesting.kds.internal.enclave.api

import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoBuf

@Serializable
sealed class EnclaveResponse {

    companion object {
        fun deserialize(request: ByteArray) =
            ProtoBuf.decodeFromByteArray(serializer(), request)
    }

    fun serialize() =
        ProtoBuf.encodeToByteArray(serializer(), this)

    @Serializable
    class PublicKey(
        val publicKey: ByteArray,
        val signature: ByteArray,
        val kdsAttestationReport: ByteArray
    ) : EnclaveResponse()

    @Serializable
    class PrivateKey(
        val kdsAttestationReport: ByteArray,
        val encryptedPrivateKey: ByteArray
    ) : EnclaveResponse()
}