package com.r3.conclave.internaltesting.kds.api.response

import kotlinx.serialization.Serializable

@Serializable
class PublicKeyResponseBody(val publicKey: ByteArray, val signature: ByteArray, val kdsAttestationReport: ByteArray)

@Serializable
class PrivateKeyResponseBody(val kdsAttestationReport: ByteArray, val encryptedPrivateKey: ByteArray)
