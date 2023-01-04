package com.r3.conclave.client.internal.kds
class KDSPublicKeyResponse constructor(
    val publicKey: ByteArray,
    val signature: ByteArray,
    val kdsAttestationReport: ByteArray
)
