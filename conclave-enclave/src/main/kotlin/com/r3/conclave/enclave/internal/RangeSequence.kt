package com.r3.conclave.enclave.internal

import java.io.DataInputStream
import java.io.DataOutputStream

class RangeSequence {
    private val ranges = mutableListOf<Range>()

    /**
     * Acknowledge (add) the sequence number (effectively register the fact that we saw this value).
     * @param sequenceNumber Mail sequence number.
     */
    fun add(sequenceNumber: Long) {
        if (ranges.isEmpty()) {
            ranges.add(Range(sequenceNumber, 1))
            return
        }

        if (ranges.last().append(sequenceNumber))
            return

        if (ranges.first().prepend(sequenceNumber))
            return

        // out of order ack
        var i = 0
        val n = ranges.size
        var merge = false
        while (i < n) {
            val range = ranges[i]

            if (range.start > sequenceNumber) {
                if (range.prepend(sequenceNumber)) {
                    merge = true
                } else if (i > 0 && ranges[i - 1].append(sequenceNumber)) {
                    merge = true
                } else {
                    ranges.add(i, Range(sequenceNumber, 1))
                }
                break
            }
            i++
        }

        if (i == n) {
            // we have checked all ranges, nothing suits - just add a new range
            ranges.add(Range(sequenceNumber, 1))
        } else if (merge && i > 0 && ranges[i - 1].merge(ranges[i])) {
            ranges.removeAt(i)
        }
    }

    /**
     * Returns next expected sequence number.
     * @return expected sequence number.
     */
    fun expected(): Long {
        if (isEmpty())
            return 0L

        val r = ranges[0]
        if (r.start > 0)
            return 0L

        return r.start + r.count
    }

    /**
     * @return true, if range sequence is empty
     */
    fun isEmpty(): Boolean {
        return ranges.isEmpty()
    }

    /**
     * Serialization. Write this object into output stream.
     * @param dos DataOutputStream
     */
    fun write(dos: DataOutputStream) {
        dos.writeInt(ranges.size)
        ranges.forEach {
            dos.writeLong(it.start)
            dos.writeLong(it.count)
        }
    }

    /**
     * Deserialization. Populate this object from input stream.
     * @param dis DataInputStream
     */
    fun read(dis: DataInputStream) {
        ranges.clear();
        val n = dis.readInt()
        repeat(n) {
            val start = dis.readLong()
            val count = dis.readLong()
            ranges.add(Range(start, count))
        }
    }

    /**
     * Deep copy.
     * @return cloned copy.
     */
    fun clone(): RangeSequence {
        val dup = RangeSequence()
        ranges.forEach {
            dup.ranges.add(it.clone())
        }
        return dup
    }

    private class Range(var start: Long, var count: Long) {
        fun clone(): Range {
            return Range(start, count)
        }

        fun merge(r2: Range): Boolean {
            if ((start + count) != r2.start)
                return false

            count += r2.count
            return true
        }

        fun append(sequenceNumber: Long): Boolean {
            if ((start + count) != sequenceNumber)
                return false

            count++
            return true
        }

        fun prepend(sequenceNumber: Long): Boolean {
            if (start != (sequenceNumber + 1))
                return false

            start = sequenceNumber
            count++
            return true
        }

        override fun toString(): String {
            return "($start,$count)"
        }
    }

    override fun toString(): String {
        return ranges.joinToString(prefix = "[", postfix = "]")
    }
}
