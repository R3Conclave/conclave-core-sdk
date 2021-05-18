package com.r3.conclave.enclave.internal

import org.assertj.core.api.Assertions.*
import org.junit.jupiter.api.Test

class SequenceRangesTest {

    @Test
    fun `mail seq ranges gap and merge`(){
        // the internals of SequenceRanges are not exposed,
        // the only way to look inside is to print it
        val ranges = RangeSequence()

        ranges.add(1)
        ranges.add(2)
        assertThat(ranges.toString()).isEqualTo("[(1,2)]")

        ranges.add(6)
        assertThat(ranges.toString()).isEqualTo("[(1,2), (6,1)]")

        ranges.add(4)
        assertThat(ranges.toString()).isEqualTo("[(1,2), (4,1), (6,1)]")

        ranges.add(3) // close the gap
        assertThat(ranges.toString()).isEqualTo("[(1,4), (6,1)]")

        ranges.add(5) // close the gap
        assertThat(ranges.toString()).isEqualTo("[(1,6)]")

        ranges.add(0) // the last missing
        assertThat(ranges.toString()).isEqualTo("[(0,7)]")
    }

    @Test
    fun `expected sequence number when ranges gap`(){
        val ranges = RangeSequence()

        // missing 0
        ranges.add(1)
        ranges.add(2)
        // missing 3
        ranges.add(4)
        ranges.add(5)
        // next seqn after backlog will be 6

        assertThat(ranges.expected()).isEqualTo(0)
        ranges.add(0)

        assertThat(ranges.expected()).isEqualTo(3)
        ranges.add(3)

        assertThat(ranges.expected()).isEqualTo(6)
    }
}

