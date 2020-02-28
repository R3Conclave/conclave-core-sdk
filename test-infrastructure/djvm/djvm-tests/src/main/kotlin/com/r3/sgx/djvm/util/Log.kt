package com.r3.sgx.djvm.util

import java.util.*

class Log {
    companion object {
        const val LIMIT = 100

        @JvmStatic
        fun recursiveStackTrace(throwable: Throwable, message: String, limit: Int) : String {
            var s = message + " caught exception " + throwable.javaClass.canonicalName + ": message: " + throwable.message +
                System.lineSeparator() + " stack trace: " + Arrays.toString(throwable.stackTrace) + System.lineSeparator()
            var cause = throwable.cause
            for (i in 0..limit) {
                if (cause == null) {
                    break
                }
                s += "cause type: " + cause.javaClass.canonicalName + ": cause message: " + cause.message +
                        System.lineSeparator() + " cause stack trace: " + Arrays.toString(cause.stackTrace) +
                        System.lineSeparator()
                cause = cause.cause
            }
            return s
        }

        @JvmStatic
        fun recursiveStackTrace(throwable: Throwable, message: String) : String {
            return recursiveStackTrace(throwable, message, LIMIT)
        }
    }
}