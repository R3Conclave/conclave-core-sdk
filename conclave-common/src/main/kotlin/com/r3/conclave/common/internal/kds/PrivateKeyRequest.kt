package com.r3.conclave.common.internal.kds

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonProperty

@JsonInclude(JsonInclude.Include.NON_NULL)
class PrivateKeyRequest @JsonCreator constructor(
        @JsonProperty("appReport")
        val appReport: String,

        @JsonProperty("name")
        val name: String,

        /**
         * The type of the master key. For now can be one of:
         * "debug"
         * "Azure"
         * "URL"
         */
        @JsonProperty("mkType")
        val mkType: String,

        @JsonProperty("policyConstraint")
        var policyConstraint: String,

        /**
         * If the master key type is "URL" then this specifies the URL of an mk
         * service that provides the key.
         */
        @JsonProperty("mkURL")
        val mkURL: String? = null,

        /**
         * If the master key type is "URL" then this specifies a constraint on the
         * enclave attestation report that must be met in order to trust the mk service.
         */
        @JsonProperty("mkConstraint")
        val mkConstraint: String? = null
)
