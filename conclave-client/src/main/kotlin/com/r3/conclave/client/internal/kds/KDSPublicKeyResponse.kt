package com.r3.conclave.client.internal.kds

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonProperty

class KDSPublicKeyResponse @JsonCreator constructor(
    @JsonProperty("publicKey")
    val publicKey: ByteArray,

    @JsonProperty("signature")
    val signature: ByteArray,

    @JsonProperty("kdsAttestationReport")
    val kdsAttestationReport: ByteArray
)
