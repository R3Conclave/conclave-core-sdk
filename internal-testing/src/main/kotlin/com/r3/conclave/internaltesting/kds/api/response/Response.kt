package com.r3.conclave.internaltesting.kds.api.response

import kotlinx.serialization.Serializable

@Serializable
data class PublicKeyResponseBody(val publicKey: String, val signature: String, val kdsAttestationReport: ByteArray)

@Serializable
data class PrivateKeyResponseBody(val kdsAttestationReport: String, val encryptedPrivateKey: ByteArray)
