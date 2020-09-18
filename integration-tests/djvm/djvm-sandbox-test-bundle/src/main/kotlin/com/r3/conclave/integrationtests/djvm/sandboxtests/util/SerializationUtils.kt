package com.r3.conclave.integrationtests.djvm.sandboxtests.util

import com.google.protobuf.BoolValue
import com.google.protobuf.ByteString
import com.google.protobuf.Int32Value
import com.google.protobuf.Int64Value
import com.r3.conclave.integrationtests.djvm.sandboxtests.proto.StringList

/**
 * Collection of utility serialization and deserialization function shared by the tests
 */
object SerializationUtils {
    @JvmStatic
    fun serializeString(data: Any?) : ByteString {
        val output = data as String
        return ByteString.copyFrom(output.toByteArray())
    }

    @JvmStatic
    fun serializeByteArray(data: Any?): ByteString {
        val output = data as ByteArray
        return ByteString.copyFrom(output)
    }

    @JvmStatic
    fun serializeInt(data: Any?): ByteString {
        val output = data as Int
        return Int32Value.newBuilder().setValue(output).build().toByteString()
    }

    @JvmStatic
    fun serializeIntArray(data: Any?): ByteString {
        val output = data as IntArray
        return com.r3.conclave.integrationtests.djvm.sandboxtests.proto.IntArray.newBuilder().addAllValues(output.asIterable()).build().toByteString()
    }

    @JvmStatic
    fun serializeLong(data: Any?): ByteString {
        val output = data as Long
        return Int64Value.newBuilder().setValue(output).build().toByteString()
    }

    @JvmStatic
    fun serializeStringArray(data: Any?): ByteString {
        val output = data as Array<*>
        val builder = StringList.newBuilder()
        output.forEach {
            builder.addValues(it as String?)
        }
        return builder.build().toByteString()
    }

    @JvmStatic
    fun serializeStringList(data: Any?): ByteString {
        val output = data as List<*>
        val builder = StringList.newBuilder()
        output.forEach {
            builder.addValues(it as String?)
        }
        return builder.build().toByteString()
    }

    @JvmStatic
    fun serializeBoolean(data: Any?): ByteString {
        return BoolValue.newBuilder().setValue(data as Boolean).build().toByteString()
    }

    @JvmStatic
    fun serializeDoubleArray(data: Any?): ByteString {
        val output = data as Array<*>
        val builder = com.r3.conclave.integrationtests.djvm.sandboxtests.proto.DoubleArray.newBuilder()
        output.forEach {
            builder.addValues(it as Double)
        }
        return builder.build().toByteString()
    }
}