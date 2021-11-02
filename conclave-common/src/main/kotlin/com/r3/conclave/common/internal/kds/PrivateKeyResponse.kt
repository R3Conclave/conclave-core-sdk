package com.r3.conclave.common.internal.kds

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonProperty

@JsonInclude(JsonInclude.Include.NON_EMPTY)
data class PrivateKeyResponse @JsonCreator constructor(
        @JsonProperty("id")
        val id: String,
        @JsonProperty("name")
        val name: String,
        @JsonProperty("mkType")
        val mkType: String,
        @JsonProperty("policyConstraint")
        val policyConstraint: String,
        @JsonProperty("privateKey")
        val privateKey: String,
        @JsonProperty("mkURL")
        val mkURL: String?,
        @JsonProperty("mkConstraint")
        val mkConstraint: String?)
