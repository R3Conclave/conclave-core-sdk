package com.r3.conclave.host.internal.kds

class KDSPrivateKeyResponse constructor(
    val kdsAttestationReport: ByteArray,
    val encryptedPrivateKey: ByteArray
)
