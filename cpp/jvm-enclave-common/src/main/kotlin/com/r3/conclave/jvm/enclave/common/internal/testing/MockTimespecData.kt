package com.r3.conclave.jvm.enclave.common.internal.testing

import kotlinx.serialization.Serializable

@Serializable
class MockTimespecData(var sec: Long = -1, var nsec: Long = -1)