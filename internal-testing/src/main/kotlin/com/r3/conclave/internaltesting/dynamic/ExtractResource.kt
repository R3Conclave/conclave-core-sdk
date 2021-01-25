package com.r3.conclave.internaltesting.dynamic

import java.io.File
import java.nio.file.Files
import java.nio.file.attribute.PosixFilePermissions

object ExtractResource {
    private val keyCache = HashMap<String, String>()

    fun extractResource(clazz: Class<*>, resourcePath: String, permissions: String): Cached<File> {
        return Cached.SingleCached(
                name = resourcePath,
                providedKey = keyCache.getOrPut(resourcePath + permissions, {
                    DigestTools.sha256String(
                            DigestTools.sha256InputStream(clazz.getResourceAsStream(resourcePath)) +
                                    permissions)
                }),
                produce = { outputDirectory ->
                    val output = File(outputDirectory.asFile, File(resourcePath).name)
                    javaClass.getResourceAsStream(resourcePath).use { input ->
                        output.outputStream().use { output ->
                            input.copyTo(output)
                        }
                    }
                    Files.setPosixFilePermissions(output.toPath(), PosixFilePermissions.fromString(permissions))
                },
                getValue = { directory -> File(directory.asFile, File(resourcePath).name) }
        )
    }
}
