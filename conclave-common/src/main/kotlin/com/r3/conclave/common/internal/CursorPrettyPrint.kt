package com.r3.conclave.common.internal

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

    private fun <S : Struct> printStructCursor(builder: StringBuilder, cursor: ByteCursor<S>, indent: Int): StringBuilder {
        builder.append(cursor.encoder.javaClass.simpleName).append('(').append(cursor.encoder.size).append(") {\n")
        val fields = cursor.encoder.javaClass.fields
        for (field in fields) {
            if (Struct.Field::class.java.isAssignableFrom(field.type)) {
                builder.appendIndent(indent + 1).append(field.name).append(" = ")
                @Suppress("UNCHECKED_CAST")
                val structField = field.get(cursor.encoder) as Struct.Field<S, Encoder<Any?>>
                printCursor(builder, cursor[structField], indent + 1)
                builder.append('\n')
            }
        }
        return builder.appendIndent(indent).append('}')
    }

    private fun <R> printCursor(builder: StringBuilder, cursor: Cursor<Encoder<R>, R>, indent: Int): StringBuilder {
        return when (cursor.encoder) {
            is Struct -> {
                @Suppress("UNCHECKED_CAST")
                printStructCursor(builder, cursor as ByteCursor<Struct>, indent)
            }
            is FixedBytes -> {
                val buffer = cursor.read() as ByteBuffer
                while (buffer.remaining() > 0) {
                    val byte = buffer.get()
                    builder.append(String.format("%02X", byte))
                }
                builder
            }
            is ReservedBytes -> {
                builder.append('<').append(cursor.encoder.size).append(" reserved bytes>")
            }
            is Enum16, is Enum32 -> {
                val fields = cursor.encoder.javaClass.fields
                val value = cursor.read()
                builder.append(value)
                for (field in fields) {
                    if (field.get(cursor.encoder) == value) {
                        return builder.append(" (").append(field.name).append(')')
                    }
                }
                builder.append(" (unknown)")
            }
            is Flags64 -> {
                val flags = cursor.read() as Long
                builder.append(String.format("%08X", flags))
                val flagNames = ArrayList<String>()
                val fields = cursor.encoder.javaClass.fields
                var reverseFlags = 0L
                for (field in fields) {
                    val fieldFlag = field.get(cursor.encoder) as? Long ?: continue
                    if (flags and fieldFlag != 0L) {
                        flagNames.add(field.name)
                        reverseFlags = reverseFlags or fieldFlag
                    }
                }
                if (flags != reverseFlags) {
                    flagNames.add("unknown")
                }
                builder.append(" (").append(flagNames.joinToString(" | ")).append(')')
            }
            is CArray<*, *> -> {
                @Suppress("UNCHECKED_CAST")
                cursor as Cursor<CArray<Any?, Encoder<Any?>>, List<Any?>>
                builder.append("[\n")
                for (i in 0 until cursor.encoder.length) {
                    builder.appendIndent(indent + 1)
                    printCursor(builder, cursor[i], indent + 1)
                    if (i < cursor.encoder.length - 1) {
                        builder.append(",\n")
                    } else {
                        builder.append('\n')
                    }
                }
                builder.appendIndent(indent).append(']')
            }
            else -> {
                builder.append(cursor.read())
            }
        }
    }

}