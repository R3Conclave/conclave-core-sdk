package com.r3.sgx.core.common

/**
 * @see ErrorHandler
 */
class ThrowingErrorHandler : ErrorHandler() {
    override fun onError(throwable: Throwable) {
        throw throwable
    }
}
