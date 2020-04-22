package com.r3.conclave.jvmtester.djvm.asserters

import com.r3.conclave.jvmtester.djvm.proto.IntArray
import com.r3.conclave.jvmtester.api.TestAsserter
import org.assertj.core.api.Assertions.assertThat
import java.util.*

class BitSetTest {
    class CreateBitSetTest : TestAsserter {
        private val BITS = byteArrayOf(0x08, 0x08, 0x08, 0x08, 0x08, 0x08, 0x08, 0x08)
        private val POSITION = 16

        override fun assertResult(testResult: ByteArray) {
            val bitset = BitSet.valueOf(BITS)
            val result = IntArray.parseFrom(testResult).valuesList.toIntArray()
            assertThat(intArrayOf(
                    bitset.length(),
                    bitset.cardinality(),
                    bitset.size(),
                    bitset.nextClearBit(POSITION),
                    bitset.previousClearBit(POSITION),
                    bitset.nextSetBit(POSITION),
                    bitset.previousSetBit(POSITION)))
                    .isEqualTo(result)
        }
    }
}