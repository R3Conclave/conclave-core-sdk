package com.r3.sgx.dynamictesting

import com.google.protobuf.GeneratedMessageV3
import com.r3.conclave.common.enclave.EnclaveCall
import com.r3.sgx.core.common.Sender
import com.r3.sgx.testing.StringHandler
import net.i2p.crypto.eddsa.spec.EdDSANamedCurveTable
import java.io.File
import java.io.FileOutputStream
import java.nio.file.Path
import java.nio.file.Paths
import java.util.*
import java.util.jar.JarFile.MANIFEST_NAME
import java.util.jar.JarOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import kotlin.jvm.internal.Intrinsics

object BuildEnclaveJarFromClass {
    sealed class Entry {
        abstract val path: Path
        data class Directory(override val path: Path) : Entry()
        data class SimpleFile(override val path: Path, val source: File) : Entry()
    }

    fun buildEnclaveJarFromClass(entryClass: Class<*>, includeClasses: List<Class<*>>, jarOutput: File) {
        val fileOutputStream = FileOutputStream(jarOutput)
        JarOutputStream(fileOutputStream).use { jar ->
            val pathCache = hashSetOf<Path>()

            // MANIFEST
            val manifestPath = Paths.get(MANIFEST_NAME)
            jar.writeDirectoryPath(pathCache, manifestPath)
            pathCache.add(manifestPath)
            jar.putNextEntry(ZipEntry(MANIFEST_NAME))
            jar.writer(Charsets.UTF_8).apply {
                write("Manifest-Version: 1.0\n")
                write("Enclave-Class: ")
                write(entryClass.name)
                write("\n")
                flush()
            }
            jar.closeEntry()

            // CLASSES
            val rootClasses = listOf(
                    entryClass,
                    com.r3.sgx.core.enclave.Enclave::class.java,
                    Sender::class.java,
                    com.r3.conclave.enclave.Enclave::class.java,
                    EnclaveCall::class.java,
                    StringHandler::class.java,
                    Intrinsics::class.java,
                    GeneratedMessageV3::class.java,
                    EdDSANamedCurveTable::class.java
            ) + includeClasses
            val locations = rootClasses.mapNotNull { it.protectionDomain?.codeSource?.location }.toSet()
            for (location in locations) {
                val file = File(location.file)
                println("Including $location")
                if (location.file.endsWith(".jar")) {
                    ZipFile(file).use { zip ->
                        for (entry in zip.entries()) {
                            val path = Paths.get(entry.name)
                            if (path in pathCache) {
                                continue
                            }
                            pathCache.add(path)
                            jar.putNextEntry(ZipEntry(entry.name))
                            val inputStream = zip.getInputStream(entry)
                            jar.write(inputStream.readBytes())
                        }
                    }
                } else if (file.isDirectory) {
                    val topPath = file.toPath()
                    val entries = arrayListOf<Entry>()
                    val queue = ArrayDeque<File>()
                    queue.add(file)
                    while (queue.isNotEmpty()) {
                        val next = queue.removeFirst()
                        val children = next.listFiles()!!
                        for (child in children) {
                            if (child.isDirectory) {
                                queue.add(child)
                                entries.add(Entry.Directory(topPath.relativize(child.toPath())))
                            } else {
                                entries.add(Entry.SimpleFile(topPath.relativize(child.toPath()), child))
                            }
                        }
                    }
                    val sortedEntries = entries.sortedBy(Entry::path)
                    for (entry in sortedEntries) {
                        if (entry.path in pathCache) {
                            continue
                        }
                        pathCache.add(entry.path)
                        jar.putNextEntry(ZipEntry(entry.path.toString()))
                        when (entry) {
                            is Entry.SimpleFile -> {
                                jar.write(entry.source.readBytes())
                            }
                        }
                    }
                } else {
                    throw IllegalStateException("Can't identify location $location")
                }
            }
        }
    }

    private fun JarOutputStream.writeDirectoryPath(pathCache: HashSet<Path>, filePath: Path) {
        val pathsToCreate = arrayListOf<Path>()
        var current: Path? = filePath.parent
        while (current != null) {
            if (current !in pathCache) {
                pathsToCreate.add(current)
                pathCache.add(current)
            }
            current = current.parent
        }
        for (pathToCreate in pathsToCreate.reversed()) {
            val entry = ZipEntry("$pathToCreate/")
            putNextEntry(entry)
        }
    }
}
