package com.r3.conclave.plugin.enclave.gradle

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import java.io.File

data class ResourcesConfig(val resources: Resources) {
    companion object {
        fun create(includes: List<String> = listOf(), excludes: List<String> = listOf()) = ResourcesConfig(
            Resources(
                includes.map { Pattern(it) },
                excludes.map { Pattern(it) })
        )
    }

    fun writeToFile(outputFile: File) {
        jacksonObjectMapper().writeValue(outputFile, this)
    }
}

data class Resources(val includes: List<Pattern>, val excludes: List<Pattern>)
data class Pattern(val pattern: String)
