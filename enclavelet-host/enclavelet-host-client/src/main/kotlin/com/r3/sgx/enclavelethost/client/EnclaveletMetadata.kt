package com.r3.sgx.enclavelethost.client

import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.databind.DeserializationContext
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.deser.std.StdDeserializer
import com.fasterxml.jackson.databind.module.SimpleModule
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.r3.sgx.core.common.attestation.Measurement
import java.io.BufferedInputStream
import java.io.File
import java.io.InputStream
import java.io.InputStreamReader

data class EnclaveletMetadata(val measurement: Measurement) {
    companion object {
        private val mapper: ObjectMapper

        /**
         * Load an EnclaveletMetadata instance stored in yaml format.
         */
        fun read(input: InputStream): EnclaveletMetadata {
            val reader = InputStreamReader(input)
            return mapper.readValue(reader, EnclaveletMetadata::class.java)
        }

        fun read(file: File) = BufferedInputStream(file.inputStream()).use { read(it) }

        init {
            val customModule = SimpleModule().addDeserializer(Measurement::class.java, MeasurementFromString)
            mapper = ObjectMapper(YAMLFactory())
                    .registerModule(KotlinModule())
                    .registerModule(customModule)
        }
    }
}

// Helper class for deserializing a Measurement in JSon parser
private object MeasurementFromString: StdDeserializer<Measurement>(Measurement::class.java) {
    override fun deserialize(p: JsonParser, ctxt: DeserializationContext): Measurement {
        val node = p.codec.readTree<JsonNode>(p)
        return Measurement.of(node.textValue())
    }
}
