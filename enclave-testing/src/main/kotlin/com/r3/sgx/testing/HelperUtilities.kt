package com.r3.sgx.testing

import java.util.function.Supplier

object HelperUtilities {
    @JvmStatic
    @Throws(InterruptedException::class)
    fun expectWithin(seconds: Int, condition: Supplier<Boolean>): Boolean {
        for (i in 0 until seconds) {
            if (condition.get()) {
                return true
            }
            Thread.sleep(1000)
        }
        return false
    }
}