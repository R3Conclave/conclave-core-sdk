package com.r3.conclave.host.internal

import com.r3.conclave.common.EnclaveMode
import io.github.classgraph.ClassGraph
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption

object NativeLoader {
    private var linkedEnclaveMode: EnclaveMode? = null

    val libsPath: Path = run {
        val tempDirectory = Files.createTempDirectory("com.r3.conclave.host-libraries")
        Runtime.getRuntime().addShutdownHook(Thread { tempDirectory.toFile().deleteRecursively() })
        tempDirectory.toAbsolutePath()
    }

    /**
     * Loads host libraries from the classpath, unpacking them temporarily to a directory. This code expects at least
     * one of:
     *     com.r3.conclave:native-host-simulation,
     *     com.r3.conclave:native-host-debug,
     *     com.r3.conclave:native-host-release
     * to be on the classpath.
     * @param enclaveMode indicates which type of library to use.
     */
    @Synchronized
    fun loadHostLibraries(enclaveMode: EnclaveMode) {
        check(UtilsOS.isLinux()) { "Failed to initialise NativeShared. NativeShared only works on Linux platforms" }
        // If host libraries have been already loaded, check input type consistency
        val localLinkedBinaryType = linkedEnclaveMode
        if (localLinkedBinaryType != null) {
            if (enclaveMode == localLinkedBinaryType) {
                return
            } else {
                throw ExceptionInInitializerError(
                    "Requested to relink host native libraries of different type. " +
                            "Loaded: $localLinkedBinaryType, " +
                            "Requested: $enclaveMode"
                )
            }
        }

        copyHostLibrariesToLibsPath(enclaveMode)
        System.load(libsPath.resolve("libjvm_host.so").toString())
        linkedEnclaveMode = enclaveMode
    }

    private fun copyHostLibrariesToLibsPath(enclaveMode: EnclaveMode) {
        val classGraph = ClassGraph()
        val modeSpecificHostLibrariesResourcePath = "com/r3/conclave/host-libraries/${enclaveMode.name.lowercase().replaceFirstChar { it.titlecase() }}"
        val sharedHostLibrariesResourcePath = "com/r3/conclave/host-libraries/shared"
        copyResourceFiles(classGraph, libsPath, modeSpecificHostLibrariesResourcePath, sharedHostLibrariesResourcePath)
    }

    private fun copyResourceFiles(classGraph: ClassGraph, destinationFolder: Path, vararg acceptPaths: String) {
        classGraph.acceptPaths(*acceptPaths)
            .scan()
            .use {
                it.allResources.forEachInputStreamThrowingIOException { resource, stream ->
                    val name = resource.path.substringAfterLast('/')
                    val destination = destinationFolder.resolve(name)
                    Files.copy(stream, destination, StandardCopyOption.REPLACE_EXISTING)
                }
            }
    }
}
