package com.r3.conclave.mail

/**
 * A minimum size policy object that specifies what the minimum size of mail messages should be, possibly taking into
 * account the size of mail previously seen. The encrypted bytes will be padded to be at least the size returned by
 * [getMinSize]. This attempts to close a message size side channel that could give away hints about the mail content.
 *
 * @see fixedMinSize
 * @see largestSeen
 * @see movingAverage
 * @see PostOffice.minSizePolicy
 */
interface MinSizePolicy {
    /**
     * Return the minimum size for the next mail message which has a body size of [bodySize].
     */
    fun getMinSize(bodySize: Int): Int

    /**
     * @suppress
     */
    companion object {
        /**
         * Return a fixed minimum size policy. All mail will have the given minimum size. It should be larger than any
         * message you reasonably expect to send.
         *
         * This is safe to use across different post offices and threads.
         */
        @JvmStatic
        fun fixedMinSize(minSize: Int): MinSizePolicy = Fixed(minSize)

        /**
         * Return a largest seen policy where the minimum size of a mail message is always the largest mail seen so far
         * including the current one.
         *
         * This is safe to use across different post offices and threads.
         */
        @JvmStatic
        fun largestSeen(): MinSizePolicy = LargestSeen()

        /**
         * Return a moving average policy where the minimum size of a mail message is the average size of all the mail
         * seen so far including the current one.
         *
         * This is safe to use across different post offices and threads.
         */
        @JvmStatic
        fun movingAverage(): MinSizePolicy = MovingAverage()
    }

    private class Fixed(private val minSize: Int) : MinSizePolicy {
        init {
            require(minSize >= 0) { "Min size cannot be negative" }
        }

        override fun getMinSize(bodySize: Int): Int = minSize
    }

    private class LargestSeen : MinSizePolicy {
        private var seen = 0

        @Synchronized
        override fun getMinSize(bodySize: Int): Int {
            if (bodySize > seen) {
                seen = bodySize
            }
            return seen
        }
    }

    private class MovingAverage : MinSizePolicy {
        private var sum = 0L
        private var count = 0

        @Synchronized
        override fun getMinSize(bodySize: Int): Int {
            sum = try {
                Math.addExact(sum, bodySize.toLong())
            } catch (e: ArithmeticException) {
                // In the (very) rare event that sum overflows we reset to the current average. This isn't equivalent
                // to using BigInteger and continuing with a true average, but it's good enough for min size and because
                // it's unlikely to happen we avoid the object churn of using BigInteger for the majority cqse.
                val resetToAverage = sum / count
                count = 1
                resetToAverage
            }
            count++
            return (sum / count).toInt()
        }
    }
}
