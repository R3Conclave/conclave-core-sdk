package com.r3.sgx.enclavelethost.server.internal.ias

import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.annotation.JsonPropertyOrder

@JsonPropertyOrder("message")
class AttestationError(@param:JsonProperty("message") val message: String)
