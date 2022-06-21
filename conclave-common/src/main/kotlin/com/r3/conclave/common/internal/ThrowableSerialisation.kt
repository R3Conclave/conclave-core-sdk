package com.r3.conclave.common.internal

import com.r3.conclave.utilities.internal.*
import java.io.DataInputStream
import java.io.DataOutputStream
import java.lang.reflect.Constructor
import java.lang.reflect.InvocationTargetException
import java.nio.ByteBuffer

object ThrowableSerialisation {
    fun serialise(throwable: Throwable): ByteArray {
        return writeData {
            serialise(this, throwable)
        }
    }

    private fun serialise(dos: DataOutputStream, root: Throwable) {
        // We need to serialise the throwable with its cause chain in reverse so that during deserialisation the cause
        // is at hand to create each throwable.
        val throwableChain = generateSequence(root, Throwable::cause).toList().asReversed()
        dos.writeList(throwableChain) { t ->
            writeUTF(t.javaClass.name)
            nullableWrite(t.message) { writeUTF(it) }
            writeList(t.stackTrace.asList()) { element ->
                writeUTF(element.className)
                writeUTF(element.methodName)
                nullableWrite(element.fileName) { writeUTF(it) }
                writeInt(element.lineNumber)
            }
            writeList(t.suppressed.asList()) { suppressed ->
                // Recurse and serialise the throwable chain of each suppressed exception.
                serialise(dos, suppressed)
            }
        }
    }

    fun deserialise(buffer: ByteBuffer): Throwable = deserialise(DataInputStream(buffer.inputStream()))

    fun deserialise(bytes: ByteArray): Throwable = deserialise(ByteBuffer.wrap(bytes))

    private fun deserialise(dis: DataInputStream): Throwable {
        var count = dis.readInt()
        var cause: Throwable? = null
        while (count-- > 0) {
            cause = deserialise(dis, cause)
        }
        // The last entry in the cause chain is the top-level throwable that we want to return.
        return requireNotNull(cause) { "Invalid serialised throwable" }
    }

    private fun deserialise(dis: DataInputStream, cause: Throwable?): Throwable {
        val exceptionClassName = dis.readUTF()
        val message = dis.nullableRead { readUTF() }
        val stackTrace = dis.readList {
            val className = readUTF()
            val methodName = readUTF()
            val fileName = nullableRead { readUTF() }
            val lineNumber = readInt()
            StackTraceElement(className, methodName, fileName, lineNumber)
        }

        val throwableClass = try {
            Class.forName(exceptionClassName).asSubclass(Throwable::class.java)
        } catch (e: Exception) {
            null
        }

        val throwable =
            throwableClass?.create(message, cause) ?: RuntimeException("$exceptionClassName: $message", cause)
        throwable.stackTrace = stackTrace

        repeat(dis.readInt()) {
            // Avoid Kotlin's Throwable as its addSuppressed method uses reflection.
            @Suppress("PLATFORM_CLASS_MAPPED_TO_KOTLIN")
            (throwable as java.lang.Throwable).addSuppressed(deserialise(dis))
        }

        return throwable
    }

    /**
     * Attempt to create the throwable using the available c'tors.
     */
    private fun Class<out Throwable>.create(message: String?, cause: Throwable?): Throwable? {
        val ctors = constructors

        // First try using a c'tor that takes in both a message and a cause.
        ctors.findByParamTypes { it.size == 2 && (it[0].isString && it[1].isParamFor(cause)) }?.tryNew(message, cause)
            ?.let { return it }
        ctors.findByParamTypes { it.size == 2 && (it[0].isParamFor(cause) && it[1].isString) }?.tryNew(cause, message)
            ?.let { return it }
        // Then try using the c'tor that only takes in a message and use initCause to set the cause.
        ctors.findByParamTypes { it.size == 1 && it[0].isString }?.tryNew(message)?.trySetCause(cause)
            ?.let { return it }

        // At this point we've not found a c'tor that takes in a message. We try to create the throwable using a c'tor
        // that only takes in a cause or the empty c'tor.
        val t = ctors.findByParamTypes { it.size == 1 && it[0].isParamFor(cause) }?.tryNew(cause)
            ?: ctors.findByParamTypes { it.isEmpty() }?.tryNew()?.trySetCause(cause)
        // If we have no message then we're good to go, otherwise we check the throwable's message in case the c'tor
        // constructed the message for us.
        return t?.takeIf { message == null || message == t.message }
    }

    @Suppress("UNCHECKED_CAST")
    private fun Array<Constructor<*>>.findByParamTypes(predicate: (Array<Class<*>>) -> Boolean): Constructor<out Throwable>? {
        return find { predicate(it.parameterTypes) } as Constructor<out Throwable>?
    }

    private val Class<*>.isString: Boolean get() = this == String::class.java

    private fun Class<*>.isParamFor(throwable: Throwable?): Boolean {
        return if (throwable != null) {
            isInstance(throwable) && this != Any::class.java // Don't match against a parameter that's declared as Object
        } else {
            Throwable::class.java.isAssignableFrom(this)
        }
    }

    /**
     * Try to create the [Throwable] using [Constructor.newInstance], returning null if it's not possible due to the
     * underlying constructor throwing an exception.
     */
    private fun Constructor<out Throwable>.tryNew(vararg ctorArgs: Any?): Throwable? {
        return try {
            newInstance(*ctorArgs)
        } catch (e: InvocationTargetException) {
            null
        }
    }

    /**
     * Try to set the [Throwable.cause] using [Throwable.initCause], returning null if it's not possible. This method is
     * only used if it's not possible to create the throwable using a constructor that takes in a cause.
     */
    private fun Throwable.trySetCause(cause: Throwable?): Throwable? {
        if (cause == null) return this
        return try {
            initCause(cause)
        } catch (e: IllegalStateException) {
            null
        }
    }

    enum class Discriminator(val value: Byte) {
        NO_ERROR(0),
        ERROR(1),
    }
}
