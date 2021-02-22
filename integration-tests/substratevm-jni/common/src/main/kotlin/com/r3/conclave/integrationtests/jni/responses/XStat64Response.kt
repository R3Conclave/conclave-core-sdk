package com.r3.conclave.integrationtests.jni.responses

import com.r3.conclave.jvm.enclave.common.internal.testing.MockStat64Data
import kotlinx.serialization.Serializable

@Serializable
data class XStat64Response(val ret: Int, val errno: Int, val buf: MockStat64Data)
