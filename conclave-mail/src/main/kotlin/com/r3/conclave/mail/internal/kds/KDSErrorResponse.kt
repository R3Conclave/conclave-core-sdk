package com.r3.conclave.mail.internal.kds

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonProperty

class KDSErrorResponse @JsonCreator constructor(
        @JsonProperty("reason")
        val reason: String)
