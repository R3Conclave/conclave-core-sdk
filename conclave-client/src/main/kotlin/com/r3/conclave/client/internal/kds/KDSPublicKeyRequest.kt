package com.r3.conclave.client.internal.kds

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonProperty
import com.r3.conclave.common.kds.MasterKeyType

@JsonInclude(JsonInclude.Include.NON_NULL)
class KDSPublicKeyRequest @JsonCreator constructor(
    @JsonProperty("name")
    val name: String,

    @JsonProperty("masterKeyType")
    val masterKeyType: MasterKeyType,

    @JsonProperty("policyConstraint")
    val policyConstraint: String
)
