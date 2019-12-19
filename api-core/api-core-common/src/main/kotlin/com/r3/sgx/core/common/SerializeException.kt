package com.r3.sgx.core.common

import java.lang.reflect.Constructor
import kotlin.Exception

internal typealias ProtobufException = com.r3.sgx.core.common.Exception

/**
 * Helpers for serializing exceptions.
 */
object SerializeException {
    /** Called from JNI */
    @JvmStatic
    fun serializeExceptionFromThrowable(throwable: Throwable): ByteArray {
        return Try.newBuilder().setFailure(javaToProtobuf(throwable)).build().toByteArray()
    }

    fun javaToProtobuf(throwable: Throwable): ProtobufException {
        val builder = ProtobufException.newBuilder()
        builder.exceptionClass = throwable.javaClass.name
        throwable.message?.let { builder.message = it }
        for (element in throwable.stackTrace) {
            val elementBuilder = StackTraceElement.newBuilder()
                    .setClassName(element.className)
                    .setMethodName(element.methodName)
                    .setLineNumber(element.lineNumber)
            element.fileName?.let { elementBuilder.fileName = it }
            builder.addStackTrace(elementBuilder)
        }
        return builder.build()
    }

    fun protobufToJava(protobufException: ProtobufException): Throwable {
        val throwable = try {
            val throwableClass = Class.forName(protobufException.exceptionClass).asSubclass(Throwable::class.java)
            val message = if (protobufException.hasMessage()) protobufException.message else null
            throwableClass.messageConstructor?.newInstance(message) ?: throwableClass.newInstance()
        } catch (e: Exception) {
            RuntimeException("${protobufException.exceptionClass}: ${protobufException.message}")
        }
        throwable.stackTrace = protobufException.stackTraceList.map {
            StackTraceElement(it.className, it.methodName, it.fileName, it.lineNumber)
        }.toTypedArray()
        return throwable
    }

    private val Class<out Throwable>.messageConstructor: Constructor<out Throwable>?
        get() {
            return try {
                getConstructor(String::class.java)
            } catch (e: NoSuchMethodException) {
                null
            }
        }

    enum class Discriminator(val value: Byte) {
        NO_ERROR(0),
        ERROR(1),
    }
}
