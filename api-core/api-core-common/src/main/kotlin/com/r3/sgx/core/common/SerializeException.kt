package com.r3.sgx.core.common

/**
 * Helpers for serializing exceptions.
 */
object SerializeException {
    /** Called from JNI */
    @JvmStatic
    fun serializeExceptionFromThrowable(throwable: Throwable): ByteArray {
        return Try.newBuilder().setFailure(exceptionFromThrowable(throwable)).build().toByteArray()
    }

    fun exceptionFromThrowable(throwable: Throwable): Exception {
        val builder = Exception.newBuilder()
        builder.message = throwable.message ?: "null"
        builder.exceptionClass = throwable.javaClass.name
        for (element in throwable.stackTrace) {
            val elementBuilder = StackTraceElement.newBuilder()
            elementBuilder
                    .setClassName(element.className)
                    .setMethodName(element.methodName).lineNumber = element.lineNumber
            if (element.fileName != null) {
                elementBuilder.fileName = element.fileName
            }
            builder.addStackTrace(elementBuilder)
        }
        return builder.build()
    }

    fun throwableFromException(exception: Exception): Throwable {
        val throwable = RuntimeException("${exception.exceptionClass}: ${exception.message}")
        throwable.stackTrace = exception.stackTraceList.map {
            StackTraceElement(it.className, it.methodName, it.fileName, it.lineNumber)
        }.toTypedArray()
        return throwable
    }

    enum class Discriminator(val value: Byte) {
        NO_ERROR(0),
        ERROR(1),
    }

}
