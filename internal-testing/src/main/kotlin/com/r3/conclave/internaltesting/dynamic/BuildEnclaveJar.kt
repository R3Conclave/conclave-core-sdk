package com.r3.conclave.internaltesting.dynamic

import com.r3.conclave.enclave.Enclave
import java.net.URI
import java.nio.file.*
import java.nio.file.StandardCopyOption.REPLACE_EXISTING
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream

object BuildEnclaveJar {
    fun build(entryClass: Class<out Enclave>, includeClasses: List<Class<*>>, outputJarFile: Path) {
        // Copy the conclave-enclave fat jar, which will act as a basis for the test enclave jar.
        BuildEnclaveJar::class.java.getResourceAsStream("conclave-enclave-fat.jar").use {
            Files.copy(it, outputJarFile)
        }

        val classLocations = (includeClasses + entryClass)
            .mapNotNull { it.protectionDomain.codeSource?.location?.let { Paths.get(it.toURI()) } }
            .toSet()

        // Add the additional classes without extracting the contents by opening it as a zip "file system".
        val jarUri = URI.create("jar:${outputJarFile.toUri()}")
        FileSystems.newFileSystem(jarUri, emptyMap<String, Any>()).use { zipFs ->
            for (location in classLocations) {
                when {
                    location.toString().endsWith(".jar") -> {
                        ZipInputStream(Files.newInputStream(location)).use { depJar ->
                            for (depEntry in depJar.entries) {
                                if (depEntry.isDirectory) continue
                                val path = zipFs.createPath(depEntry.name)
                                Files.copy(depJar, path, REPLACE_EXISTING)
                            }
                        }
                    }
                    Files.isDirectory(location) -> {
                        Files.walk(location).use { paths ->
                            paths.filter { Files.isRegularFile(it) }.forEach { file ->
                                val path = zipFs.createPath(location.relativize(file).toString())
                                Files.copy(file, path, REPLACE_EXISTING)
                            }
                        }
                    }
                    else -> throw IllegalStateException("Can't identify location $location")
                }
            }
            // The problem of including the entire location of the classes is that two enclave classes from the same
            // location will produce the same jar file and thus the same MRENCLAVE. So we add this file to deduplicate
            // the jars.
            Files.createFile(zipFs.createPath("dynamic-enclave-$entryClass"))
        }
    }

    private fun FileSystem.createPath(name: String): Path {
        val path = getPath(name)
        path.parent?.let(Files::createDirectories)
        return path
    }

    private val ZipInputStream.entries get(): Sequence<ZipEntry> = generateSequence(nextEntry) { nextEntry }
}
