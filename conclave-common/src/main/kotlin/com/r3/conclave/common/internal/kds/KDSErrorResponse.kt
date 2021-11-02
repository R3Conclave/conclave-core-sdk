package com.r3.conclave.common.internal.kds

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonProperty

class KDSErrorResponse @JsonCreator constructor(
        @JsonProperty("reason")
        val reason: String)
