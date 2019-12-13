package com.r3.sgx.core.host.internal

import com.r3.sgx.core.host.EnclaveLoadMode
import java.io.File
import java.net.URL
import java.nio.file.*
import java.util.*

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
        val classLoader = NativeLoader::class.java.classLoader
        val resourcePaths = getHostLibPaths(classLoader, enclaveLoadMode)
        val temporaryDirectory = createTemporaryDirectory()
        for (path in resourcePaths) {
            val destination = File(temporaryDirectory, path.fileName.toString())
            val fileStream = classLoader.getResourceAsStream(path.toString().drop(1))
                    ?: throw Exception("Can't open $path as resource")
            Files.copy(fileStream, destination.toPath(), StandardCopyOption.REPLACE_EXISTING)
        }
        System.load(File(temporaryDirectory, "libjvm_host.so").absolutePath)
        linkedEnclaveLoadMode = enclaveLoadMode
    }

    private fun createTemporaryDirectory(): File {
        val id = UUID.randomUUID().toString()
        val temporaryDirectory = File(System.getProperty("java.io.tmpdir"), "com.r3.sgx.host-libraries.$id")
        temporaryDirectory.mkdir()
        temporaryDirectory.deleteOnExit()
        Runtime.getRuntime().addShutdownHook(Thread {
            temporaryDirectory.deleteRecursively()
        })
        return temporaryDirectory
    }

    private fun getHostLibPaths(classLoader: ClassLoader, enclaveLoadMode: EnclaveLoadMode): List<Path> {
        val hostLibPrefix = "com/r3/sgx/host-libraries/"
        val hostLibrariesResourcePath = hostLibPrefix + when (enclaveLoadMode) {
            EnclaveLoadMode.SIMULATION -> "Simulation"
            EnclaveLoadMode.DEBUG -> "Debug"
            EnclaveLoadMode.RELEASE -> "Release"
        }
        val librariesUrls = classLoader.getResources(hostLibrariesResourcePath).toList()
        when (librariesUrls.size) {
            0 -> throw IllegalStateException(
                    "Cannot find native host libraries, make sure they are on the classpath (com.r3.sgx:native-host-*)")
            1 -> return getNativeResourcePaths(librariesUrls[0], hostLibrariesResourcePath)
            else -> throw IllegalStateException(
                    "Found several sources of native host libraries ($librariesUrls), make sure to only provide a single one")
        }
    }

    private fun getNativeResourcePaths(libraryUrl: URL, hostLibrariesResourcePath: String): List<Path> {
        val resourcePaths = ArrayList<Path>()
        FileSystems.newFileSystem(libraryUrl.toURI(), emptyMap<String, Any?>()).use {
            for (path in Files.walk(it.getPath(hostLibrariesResourcePath))) {
                if (!Files.isDirectory(path)) {
                    resourcePaths.add(path)
                }
            }
        }
        return resourcePaths
    }
}
