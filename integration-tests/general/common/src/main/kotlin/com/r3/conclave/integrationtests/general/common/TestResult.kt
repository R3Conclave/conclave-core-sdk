package com.r3.conclave.integrationtests.general.common

import kotlinx.serialization.Serializable

@Serializable
data class TestResult(val success: Boolean, val message: String)
