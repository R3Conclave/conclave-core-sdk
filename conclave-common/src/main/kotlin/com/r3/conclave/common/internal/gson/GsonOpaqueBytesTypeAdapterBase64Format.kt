package com.r3.conclave.common.internal.gson

import com.google.gson.TypeAdapter
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonToken
import com.google.gson.stream.JsonWriter
import com.r3.conclave.common.OpaqueBytes
import java.io.IOException
import java.util.*

class GsonOpaqueBytesTypeAdapterBase64Format: TypeAdapter<OpaqueBytes>()  {

    @Throws(IOException::class)
    override fun write(jsonWriter: JsonWriter, opaqueBytes: OpaqueBytes?) {
        if (opaqueBytes == null) {
            jsonWriter.nullValue()
            return
        }
        jsonWriter.value(Base64.getEncoder().encodeToString(opaqueBytes.bytes))
    }

    @Throws(IOException::class)
    override fun read(jsonReader: JsonReader): OpaqueBytes? {
        if (jsonReader.peek() == JsonToken.NULL) {
            jsonReader.nextNull()
            return null
        }
        return OpaqueBytes(Base64.getDecoder().decode(jsonReader.nextString()))
    }
}