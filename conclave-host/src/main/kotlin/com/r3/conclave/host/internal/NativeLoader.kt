package com.r3.conclave.host.internal

import com.r3.conclave.common.EnclaveMode
import io.github.classgraph.ClassGraph
import java.nio.file.Files
import java.nio.file.Path

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

        val hostLibrariesResourcePath = "com/r3/conclave/host-libraries/${enclaveMode.name.toLowerCase().capitalize()}"

        ClassGraph()
                .whitelistPaths(hostLibrariesResourcePath)
                .scan()
                .use {
                    it.allResources.forEachInputStream { resource, stream ->
                        val name = resource.path.substringAfterLast('/')
                        val destination = libsPath.resolve(name)
                        Files.copy(stream, destination)
                    }
                }

        System.load(libsPath.resolve("libjvm_host.so").toString())
        linkedEnclaveMode = enclaveMode
    }
}
