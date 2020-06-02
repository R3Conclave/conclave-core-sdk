package com.r3.conclave.common.internal.handler

import com.r3.conclave.common.internal.handler.ErrorHandler

/**
 * @see ErrorHandler
 *
 * Preserves Java langauge behaviour with regards to checked exceptions, i.e. checked exceptions are thrown wrapped in
 * a RuntimeException and unchecked exceptions are thrown as is.
 */
class ThrowingErrorHandler : ErrorHandler() {
    override fun onError(throwable: Throwable) {
        if (throwable is RuntimeException || throwable is Error) {
            throw throwable
        } else {
            throw RuntimeException(throwable)
        }
    }
}
