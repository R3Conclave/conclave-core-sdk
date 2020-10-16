package com.r3.conclave.common.internal

import com.r3.conclave.utilities.internal.getRemainingBytes
import com.r3.conclave.utilities.internal.toHexString
import java.nio.ByteBuffer
import java.util.*

/**
 * A pretty printer for [Cursor]s.
 */
object CursorPrettyPrint {
    fun <R> print(cursor: Cursor<Encoder<R>, R>): String {
        val builder = StringBuilder()
        printCursor(builder, cursor, 0)
        return builder.toString()
    }

    private fun StringBuilder.appendIndent(indent: Int): StringBuilder {
        val chars = CharArray(2 * indent)
        Arrays.fill(chars, ' ')
        append(chars)
        return this
    }

    private fun printStructCursor(builder: StringBuilder, cursor: Cursor<*, *>, indent: Int): StringBuilder {
        builder.append(cursor.encoder.javaClass.simpleName).append('(').append(cursor.size).append(") {\n")
        val fields = cursor.encoder.javaClass.fields
        for (field in fields) {
            if (AbstractStruct.Field::class.java.isAssignableFrom(field.type)) {
                builder.appendIndent(indent + 1).append(field.name).append(" = ")
                @Suppress("UNCHECKED_CAST")
                val structField = field.get(cursor.encoder) as AbstractStruct.Field<Encoder<*>, Encoder<Any?>>
                printCursor(builder, cursor[structField], indent + 1)
                builder.append('\n')
            }
        }
        return builder.appendIndent(indent).append('}')
    }

    private fun <R> printCursor(builder: StringBuilder, cursor: Cursor<Encoder<R>, R>, indent: Int): StringBuilder {
        return when (cursor.encoder) {
            is AbstractStruct -> {
                printStructCursor(builder, cursor, indent)
            }
            is ReservedBytes -> {
                builder.append('<').append(cursor.encoder.size).append(" reserved bytes>")
            }
            is EnumEncoder<*> -> {
                val value = cursor.read()
                builder.append(value)
                val valueName = cursor.encoder.values.entries.find { it.value == value }?.key ?: "unknown"
                builder.append(" (").append(valueName).append(')')
            }
            is Flags64 -> {
                val flags = cursor.read() as Long
                builder.append(String.format("%08X", flags))
                val flagNames = ArrayList<String>()
                var reverseFlags = 0L
                for ((name, fieldFlag) in cursor.encoder.values) {
                    if (flags and fieldFlag != 0L) {
                        flagNames.add(name)
                        reverseFlags = reverseFlags or fieldFlag
                    }
                }
                if (flags != reverseFlags) {
                    flagNames.add("unknown")
                }
                builder.append(" (").append(flagNames.joinToString(" | ")).append(')')
            }
            is Flags16 -> {
                val flags = cursor.read() as Int
                builder.append(String.format("%04X", flags))
                val flagNames = ArrayList<String>()
                var reverseFlags = 0
                for ((name, fieldFlag) in cursor.encoder.values) {
                    if (flags and fieldFlag != 0) {
                        flagNames.add(name)
                        reverseFlags = reverseFlags or fieldFlag
                    }
                }
                if (flags != reverseFlags) {
                    flagNames.add("unknown")
                }
                builder.append(" (").append(flagNames.joinToString(" | ")).append(')')
            }
            else -> {
                val value = cursor.read()
                if (value is ByteBuffer) {
                    builder.append(value.getRemainingBytes(avoidCopying = true).toHexString())
                } else {
                    builder.append(value)
                }
            }
        }
    }
}
