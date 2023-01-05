package com.r3.conclave.plugin.enclave.gradle

import org.gradle.internal.impldep.com.google.gson.Gson
import java.io.File
import java.io.FileWriter
import java.io.IOException




data class ResourcesConfig(val resources: Resources) {
    companion object {
        fun create(includes: List<String> = listOf(), excludes: List<String> = listOf()) = ResourcesConfig(
            Resources(
                includes.map { Pattern(it) },
                excludes.map { Pattern(it) })
        )
    }

    fun writeToFile(outputFile: File) {
        FileWriter(outputFile).use { writer -> Gson().toJson(this, writer) }
    }
}

data class Resources(val includes: List<Pattern>, val excludes: List<Pattern>)
data class Pattern(val pattern: String)
