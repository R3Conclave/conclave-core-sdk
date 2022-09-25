package com.r3.conclave.common.internal

import java.nio.ByteBuffer

interface CallHandler {
    fun handleCall(parameterBuffer: ByteBuffer): ByteBuffer?
}
