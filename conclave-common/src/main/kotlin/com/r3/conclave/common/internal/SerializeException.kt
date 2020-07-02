package com.r3.conclave.common.internal

import com.r3.conclave.utilities.internal.*
import java.lang.reflect.Constructor

object SerializeException {
    fun serialise(throwable: Throwable): ByteArray {
        return writeData {
            writeUTF(throwable.javaClass.name)
            nullableWrite(throwable.message) { writeUTF(it) }
            writeList(throwable.stackTrace.asList()) { element ->
                writeUTF(element.className)
                writeUTF(element.methodName)
                nullableWrite(element.fileName) { writeUTF(it) }
                writeInt(element.lineNumber)
            }
        }
    }

    fun deserialise(bytes: ByteArray): Throwable {
        val dis = bytes.dataStream()
        val exceptionClassName = dis.readUTF()
        val message = dis.nullableRead { readUTF() }
        val stackTrace = dis.readList {
            val className = readUTF()
            val methodName = readUTF()
            val fileName = nullableRead { readUTF() }
            val lineNumber = readInt()
            StackTraceElement(className, methodName, fileName, lineNumber)
        }

        val throwable = try {
            val throwableClass = Class.forName(exceptionClassName).asSubclass(Throwable::class.java)
            throwableClass.messageConstructor?.newInstance(message) ?: throwableClass.getDeclaredConstructor().newInstance()
        } catch (e: Exception) {
            RuntimeException("$exceptionClassName: $message")
        }
        throwable.stackTrace = stackTrace
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
