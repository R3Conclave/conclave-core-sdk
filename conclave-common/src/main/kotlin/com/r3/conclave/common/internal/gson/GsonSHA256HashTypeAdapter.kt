package com.r3.conclave.common.internal.gson

import com.google.gson.TypeAdapter
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonToken
import com.google.gson.stream.JsonWriter
import com.r3.conclave.common.SHA256Hash
import java.io.IOException

class GsonSHA256HashTypeAdapter: TypeAdapter<SHA256Hash>()  {

    @Throws(IOException::class)
    override fun write(jsonWriter: JsonWriter, shA256Hash: SHA256Hash?) {
        if (shA256Hash == null) {
            jsonWriter.nullValue()
            return
        }
        jsonWriter.value(shA256Hash.toString())
    }

    @Throws(IOException::class)
    override fun read(jsonReader: JsonReader): SHA256Hash? {
        if (jsonReader.peek() == JsonToken.NULL) {
            jsonReader.nextNull()
            return null
        }
        return SHA256Hash.parse(jsonReader.nextString())
    }
}