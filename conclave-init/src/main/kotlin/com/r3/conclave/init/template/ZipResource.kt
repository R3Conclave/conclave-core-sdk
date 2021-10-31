package com.r3.conclave.init.template

import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.util.zip.ZipInputStream
import kotlin.io.path.createDirectories
import kotlin.io.path.createTempDirectory


class ZipResource(
    private val name: String = "/template.zip",
    private val outputDir: Path = createTempDirectory("conclave-template")
) {
    fun extractFiles(): Path {
        val zipResource = this::class.java.getResourceAsStream(name)
        ZipInputStream(zipResource).use { it.copyFiles(outputDir) }

        return outputDir
    }

    private fun ZipInputStream.copyFiles(target: Path) {
        while (true) {
            val entry = nextEntry ?: break
            val file = target.resolve(entry.name)
            if (!file.normalize().startsWith(target)) throw IOException("Bad zip entry")
            if (entry.isDirectory) continue

            file.parent.createDirectories()
            Files.copy(this, file)
        }
    }
}
