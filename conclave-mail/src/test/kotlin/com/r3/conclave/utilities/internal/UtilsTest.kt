package com.r3.conclave.utilities.internal

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.nio.ByteBuffer

class UtilsTest {
    @Test
    fun `absolute getSlice`() {
        val original = ByteBuffer.wrap(ByteArray(10) { index -> index.toByte() })

        with(original.getSlice(size = 0, index = 5)) {
            assertThat(position()).isZero()
            assertThat(remaining()).isZero()
            assertThat(limit()).isZero()
            assertThat(capacity()).isZero()
        }
        assertThat(original.position()).isZero()

        with(original.getSlice(size = 3, index = 5)) {
            assertThat(position()).isZero()
            assertThat(remaining()).isEqualTo(3)
            assertThat(limit()).isEqualTo(3)
            assertThat(capacity()).isEqualTo(3)
            assertThat(getRemainingBytes()).isEqualTo(byteArrayOf(5, 6, 7))
        }
        assertThat(original.position()).isZero()
    }
}
