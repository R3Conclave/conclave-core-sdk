package com.r3.conclave.common.internal.kds

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonProperty

@JsonInclude(JsonInclude.Include.NON_NULL)
class PrivateKeyRequest @JsonCreator constructor(
        @JsonProperty("appAttestationReport")
        val appAttestationReport: ByteArray,

        @JsonProperty("name")
        val name: String,

        /**
         * The type of the master key. For now can be one of:
         * "debug"
         * "Azure"
         * "URL"
         */
        @JsonProperty("masterKeyType")
        val masterKeyType: String,

        @JsonProperty("policyConstraint")
        var policyConstraint: String
)
