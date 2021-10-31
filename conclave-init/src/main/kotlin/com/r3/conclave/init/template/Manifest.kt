package com.r3.conclave.init.template

import java.io.IOException

class ManifestFiles(contents: Sequence<String>) {
    companion object {
        fun load(): ManifestFiles {
            val files = this::class.java.classLoader.getResources("META-INF/MANIFEST.MF").asSequence()
            return ManifestFiles(files.map { it.readText() })
        }
    }

    private val parsed: Sequence<Map<String, String>> by lazy { contents.map(this::parseManifest) }

    private fun parseManifest(text: String): Map<String, String> {
        return text.lines()
            .map { line -> line.split(": ") }
             // I'm not sure why this filter is needed, but `IndexOutOfBoundsException` gets thrown otherwise
            .filter { it.size == 2 }
            .associate { (key, value) -> key to value }
    }

    val conclaveVersion: String by lazy {
        for (manifest in parsed) {
            val version = manifest["Conclave-Release-Version"]
            if (version != null) return@lazy version
        }
        throw IOException("Could not get ConclaveVersion from META-INF/MANIFEST.MF")
    }
}