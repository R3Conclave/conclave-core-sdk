package com.r3.conclave.host.internal.kds

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonProperty

class KDSPrivateKeyResponse @JsonCreator constructor(
    @JsonProperty("kdsAttestationReport")
    val kdsAttestationReport: ByteArray,

    @JsonProperty("encryptedPrivateKey")
    val encryptedPrivateKey: ByteArray
)
