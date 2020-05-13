package com.r3.sgx.core.common

import com.r3.conclave.common.internal.nullableRead
import com.r3.conclave.common.internal.nullableWrite
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.lang.reflect.Constructor

object SerializeException {
    fun serialise(throwable: Throwable): ByteArray {
        val baos = ByteArrayOutputStream()
        val dos = DataOutputStream(baos)
        dos.writeUTF(throwable.javaClass.name)
        dos.nullableWrite(throwable.message) { writeUTF(it) }
        throwable.stackTrace.let { stackTrace ->
            dos.writeInt(stackTrace.size)
            for (element in stackTrace) {
                dos.writeUTF(element.className)
                dos.writeUTF(element.methodName)
                dos.nullableWrite(element.fileName) { writeUTF(it) }
                dos.writeInt(element.lineNumber)
            }
        }
        return baos.toByteArray()
    }

    fun deserialise(bytes: ByteArray): Throwable {
        val dis = DataInputStream(bytes.inputStream())
        val exceptionClassName = dis.readUTF()
        val message = dis.nullableRead { readUTF() }
        val stackTrace = Array(dis.readInt()) {
            val className = dis.readUTF()
            val methodName = dis.readUTF()
            val fileName = dis.nullableRead { readUTF() }
            val lineNumber = dis.readInt()
            StackTraceElement(className, methodName, fileName, lineNumber)
        }

        val throwable = try {
            val throwableClass = Class.forName(exceptionClassName).asSubclass(Throwable::class.java)
            throwableClass.messageConstructor?.newInstance(message) ?: throwableClass.newInstance()
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
