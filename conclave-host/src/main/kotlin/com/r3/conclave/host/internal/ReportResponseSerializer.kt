package com.r3.conclave.host.internal

import com.fasterxml.jackson.annotation.JsonFormat
import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL
import com.fasterxml.jackson.core.JsonGenerator
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializerProvider
import com.fasterxml.jackson.databind.annotation.JsonSerialize
import com.fasterxml.jackson.databind.ser.std.StdSerializer
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.r3.conclave.common.internal.toHexString
import java.time.Instant

object ReportResponseSerializer {
    fun register(objectMapper: ObjectMapper): ObjectMapper {
        objectMapper.registerModule(KotlinModule())
        objectMapper.registerModule(JavaTimeModule())
        objectMapper.addMixIn(ReportResponse::class.java, ReportResponseMixin::class.java)
        return objectMapper
    }

    @Suppress("unused")
    @JsonInclude(NON_NULL)
    private interface ReportResponseMixin {
        @get:JsonSerialize(using = Base16Serializer::class)
        val platformInfoBlob: ByteArray?

        @get:JsonSerialize(using = Base16Serializer::class)
        val pseManifestHash: ByteArray?

        @get:JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss.SSSSSS", timezone = "UTC")
        val timestamp: Instant
    }

    private class Base16Serializer : StdSerializer<ByteArray>(ByteArray::class.java) {
        override fun serialize(value: ByteArray, gen: JsonGenerator, provider: SerializerProvider) {
            gen.writeString(value.toHexString())
        }
    }
}
