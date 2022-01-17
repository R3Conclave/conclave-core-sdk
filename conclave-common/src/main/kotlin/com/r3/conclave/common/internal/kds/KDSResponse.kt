package com.r3.conclave.common.internal.kds

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonProperty

class KDSResponse @JsonCreator constructor(
        @JsonProperty("id")
        val id: String,
        @JsonProperty("kdsAttestationReport")
        val kdsAttestationReport: ByteArray,
        @JsonProperty("data")
        val data: ByteArray
)
