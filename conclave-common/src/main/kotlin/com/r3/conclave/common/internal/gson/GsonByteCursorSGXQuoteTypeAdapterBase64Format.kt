package com.r3.conclave.common.internal.gson

import com.fasterxml.jackson.core.JsonGenerator
import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.databind.DeserializationContext
import com.fasterxml.jackson.databind.JsonDeserializer
import com.fasterxml.jackson.databind.JsonSerializer
import com.fasterxml.jackson.databind.SerializerProvider
import com.google.gson.TypeAdapter
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonToken
import com.google.gson.stream.JsonWriter
import com.r3.conclave.common.OpaqueBytes
import com.r3.conclave.common.SHA256Hash
import com.r3.conclave.common.internal.ByteCursor
import com.r3.conclave.common.internal.Cursor
import com.r3.conclave.common.internal.SgxQuote
import java.io.IOException
import java.util.*

class GsonByteCursorSGXQuoteTypeAdapterBase64Format: TypeAdapter<ByteCursor<SgxQuote>>()  {

    @Throws(IOException::class)
    override fun write(jsonWriter: JsonWriter, sgxQuote: ByteCursor<SgxQuote>?) {
        if (sgxQuote == null) {
            jsonWriter.nullValue()
            return
        }
        jsonWriter.value(Base64.getEncoder().encodeToString(sgxQuote.bytes))
    }

    @Throws(IOException::class)
    override fun read(jsonReader: JsonReader): ByteCursor<SgxQuote>? {
        if (jsonReader.peek() == JsonToken.NULL) {
            jsonReader.nextNull()
            return null
        }
        return Cursor.wrap(SgxQuote, Base64.getDecoder().decode(jsonReader.nextString()))
    }

}
