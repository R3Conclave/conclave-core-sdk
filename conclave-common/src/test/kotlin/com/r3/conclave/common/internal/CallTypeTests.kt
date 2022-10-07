package com.r3.conclave.common.internal

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class CallTypeTests {
    @Test
    fun `host call type can always be encoded as a byte`() {
        assertThat(HostCallType.values().size).isLessThanOrEqualTo(Byte.MAX_VALUE.toInt())
    }

    @Test
    fun `enclave call type can always be encoded as a byte`() {
        assertThat(EnclaveCallType.values().size).isLessThanOrEqualTo(Byte.MAX_VALUE.toInt())
    }

    @Test
    fun `call interface message type can always be encoded as a byte`() {
        assertThat(CallInterfaceMessageType.values().size).isLessThanOrEqualTo(Byte.MAX_VALUE.toInt())
    }
}
