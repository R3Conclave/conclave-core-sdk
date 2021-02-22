package com.r3.conclave.jvm.enclave.common.internal.testing

import kotlinx.serialization.Serializable

@Serializable
data class MockStat64Data(val timespec: MockTimespecData = MockTimespecData())