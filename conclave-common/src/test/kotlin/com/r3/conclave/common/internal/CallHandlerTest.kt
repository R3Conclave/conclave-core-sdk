package com.r3.conclave.common.internal

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import java.nio.ByteBuffer


class CallHandlerTest {
    @ParameterizedTest
    @ValueSource(longs=[0, 10, 38, 456789, Long.MAX_VALUE])
    fun `dynamic length field encode decode test`(testValue: Long) {
        val outBuffer = ByteBuffer.allocate(32)
        outBuffer.encodeDynamicLengthField(testValue)
        val outputValue = ByteBuffer.wrap(outBuffer.array()).decodeDynamicLengthField()
        assertThat(outputValue).isEqualTo(testValue)
    }
}
