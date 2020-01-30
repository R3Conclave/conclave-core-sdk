package com.r3.sgx.core.host.internal

import com.r3.sgx.core.host.EnclaveLoadMode
import io.github.classgraph.ClassGraph
import java.nio.file.Files
import java.nio.file.Path

object NativeLoader {
    private var linkedEnclaveLoadMode: EnclaveLoadMode? = null

    /**
     * Loads host libraries from the classpath, unpacking them temporarily to a directory. This code expects at least
     * one of:
     *     com.r3.sgx:native-host-simulation,
     *     com.r3.sgx:native-host-debug,
     *     com.r3.sgx:native-host-release
     * to be on the classpath.
     * @param enclaveLoadMode indicates which type of library to use.
     */
    @Synchronized
    fun loadHostLibraries(enclaveLoadMode: EnclaveLoadMode) {
        // If host libraries have been already loaded, check input type consistency
        val localLinkedBinaryType = linkedEnclaveLoadMode
        if (localLinkedBinaryType != null) {
            if (enclaveLoadMode == localLinkedBinaryType) {
                return
            } else {
                throw ExceptionInInitializerError(
                        "Requested to relink host native libraries of different type. " +
                                "Loaded: $localLinkedBinaryType, " +
                                "Requested: $enclaveLoadMode"
                )
            }
        }

        val tempDirectory = createTempDirectory()

        val hostLibrariesResourcePath = "com/r3/sgx/host-libraries/${enclaveLoadMode.name.toLowerCase().capitalize()}"

        ClassGraph()
                .whitelistPaths(hostLibrariesResourcePath)
                .scan()
                .use {
                    it.allResources.forEachInputStream { resource, stream ->
                        val name = resource.path.substringAfterLast('/')
                        val destination = tempDirectory.resolve(name)
                        Files.copy(stream, destination)
                    }
                }

        System.load(tempDirectory.resolve("libjvm_host.so").toAbsolutePath().toString())
        linkedEnclaveLoadMode = enclaveLoadMode
    }

    private fun createTempDirectory(): Path {
        val tempDirectory = Files.createTempDirectory("com.r3.sgx.host-libraries")
        Runtime.getRuntime().addShutdownHook(Thread { tempDirectory.toFile().deleteRecursively() })
        return tempDirectory
    }
}
