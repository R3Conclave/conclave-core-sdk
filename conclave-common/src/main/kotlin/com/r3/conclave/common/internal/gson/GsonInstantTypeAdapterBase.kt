package com.r3.conclave.common.internal.gson

import com.google.gson.TypeAdapter
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonToken
import com.google.gson.stream.JsonWriter
import java.io.IOException
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.*

abstract class GsonInstantTypeAdapterBase(patternFormat: PatternFormat): TypeAdapter<Instant>() {
    enum class PatternFormat(val value: String) {
        TIMESTAMP( "yyyy-MM-dd'T'HH:mm:ss.SSSSSS"),
        DATE_TIME( "yyyy-MM-dd'T'HH:mm:ss'Z'"),
    }

    companion object {
        private val ZONE_ID_UTC = ZoneId.of("UTC")
    }

    private val dateTimeFormatter = DateTimeFormatter.ofPattern(patternFormat.value).withZone(ZONE_ID_UTC)

    @Throws(IOException::class)
    override fun write(jsonWriter: JsonWriter, instant: Instant?) {
        if (instant == null) {
            jsonWriter.nullValue()
            return
        }
        jsonWriter.value(dateTimeFormatter.format(instant))
    }


    @Throws(IOException::class)
    override fun read(jsonReader: JsonReader): Instant? {
        if (jsonReader.peek() == JsonToken.NULL) {
            jsonReader.nextNull()
            return null
        }
        return LocalDateTime.parse(jsonReader.nextString(), dateTimeFormatter).atZone(ZONE_ID_UTC).toInstant()
    }
}

