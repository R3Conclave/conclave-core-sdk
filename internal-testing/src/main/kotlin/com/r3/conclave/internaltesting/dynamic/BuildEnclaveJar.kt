package com.r3.conclave.internaltesting.dynamic

import com.r3.conclave.enclave.Enclave
import java.io.File.pathSeparator
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.jar.JarOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream

object BuildEnclaveJar {
    private val enclaveDependencies: Set<Path>

    init {
        val enclaveDependencyNames = BuildEnclaveJar::class.java.getResourceAsStream("/enclave-deps.txt").reader().useLines { it.toSet() }
        enclaveDependencies = System.getProperty("java.class.path")
                .splitToSequence(pathSeparator)
                .map { Paths.get(it) }
                .filter { it.fileName.toString() in enclaveDependencyNames }
                .toSet()
    }

    fun build(entryClass: Class<out Enclave>, includeClasses: List<Class<*>>, outputJarFile: Path) {
        val classLocations = (includeClasses + entryClass)
                .mapNotNull { it.protectionDomain.codeSource?.location?.let { Paths.get(it.toURI()) } }
                .toSet()
        val allLocations = enclaveDependencies + classLocations

        JarOutputStream(Files.newOutputStream(outputJarFile)).use { outputJar ->
            val pathCache = HashSet<Path>()
            for (location in allLocations) {
                println("Including $location")
                if (location.toString().endsWith(".jar")) {
                    ZipInputStream(Files.newInputStream(location)).use { depJar ->
                        depJar.entries
                                .filter { pathCache.add(Paths.get(it.name)) }
                                .forEach {
                                    outputJar.putNextEntry(ZipEntry(it.name))
                                    depJar.copyTo(outputJar)
                                }
                    }
                } else if (Files.isDirectory(location)) {
                    Files.walk(location).use { paths ->
                        paths.filter { Files.isRegularFile(it) }.forEach { file ->
                            val entryPath = location.relativize(file)
                            if (pathCache.add(entryPath)) {
                                outputJar.putNextEntry(ZipEntry(entryPath.toString()))
                                Files.copy(file, outputJar)
                            }
                        }
                    }
                } else {
                    throw IllegalStateException("Can't identify location $location")
                }
            }
        }
    }

    private val ZipInputStream.entries get(): Sequence<ZipEntry> = generateSequence(nextEntry) { nextEntry }
}
