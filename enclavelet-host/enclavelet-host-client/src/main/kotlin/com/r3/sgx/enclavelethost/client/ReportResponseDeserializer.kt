package com.r3.sgx.enclavelethost.client

import com.fasterxml.jackson.annotation.JsonFormat
import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL
import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.databind.DeserializationContext
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.annotation.JsonDeserialize
import com.fasterxml.jackson.databind.deser.std.StdDeserializer
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.google.common.io.BaseEncoding
import com.r3.conclave.host.internal.ReportResponse
import java.time.Instant

object ReportResponseDeserializer {
    fun register(objectMapper: ObjectMapper): ObjectMapper {
        objectMapper.registerModule(KotlinModule())
        objectMapper.registerModule(JavaTimeModule())
        objectMapper.addMixIn(ReportResponse::class.java, ReportResponseMixin::class.java)
        return objectMapper
    }

    @Suppress("unused")
    @JsonInclude(NON_NULL)
    private interface ReportResponseMixin {
        @get:JsonDeserialize(using = Base16Deserializer::class)
        val platformInfoBlob: ByteArray?

        @get:JsonDeserialize(using = Base16Deserializer::class)
        val pseManifestHash: ByteArray?

        @get:JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss.SSSSSS", timezone = "UTC")
        val timestamp: Instant
    }

    private class Base16Deserializer : StdDeserializer<ByteArray>(ByteArray::class.java) {
        override fun deserialize(p: JsonParser, ctxt: DeserializationContext): ByteArray {
            return BaseEncoding.base16().decode(p.valueAsString)
        }
    }
}
