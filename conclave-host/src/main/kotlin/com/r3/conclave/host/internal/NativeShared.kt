package com.r3.conclave.host.internal

import io.github.classgraph.ClassGraph
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption

/**
 * The JNI interface of the host shared between Simulation, Release and Debug enclaves. Requires symbols loaded with [NativeLoaderShared]
 */
object NativeShared {
    private val logger = loggerFor<NativeShared>()

    init {
        val tempDirectory = createTempDirectory()
        logger.debug("Unpacking native libraries to $tempDirectory")
        val hostLibrariesResourcePath = "com/r3/conclave/host-libraries/shared"

        ClassGraph()
                .whitelistPaths(hostLibrariesResourcePath)
                .scan()
                .use {
                    it.allResources.forEachInputStream { resource, stream ->
                        val name = resource.path.substringAfterLast('/')
                        val destination = tempDirectory.resolve(name)
                        // REPLACE_EXISTING is a hack to work around an issue observed by IntellectEU that has not
                        // yet been diagnosed. See bug CON-239.
                        logger.debug("Copying $name to $destination")
                        Files.copy(stream, destination, StandardCopyOption.REPLACE_EXISTING)
                    }
                }

        val libPath = tempDirectory.resolve("libjvm_host_shared.so").toAbsolutePath().toString()
        logger.debug("Loading $libPath")
        System.load(libPath)
    }

    private fun createTempDirectory(): Path {
        val tempDirectory = Files.createTempDirectory("com.r3.conclave.host-libraries-shared")
        Runtime.getRuntime().addShutdownHook(Thread { tempDirectory.toFile().deleteRecursively() })
        return tempDirectory
    }

    external fun checkPlatformSupportsEnclaves(enableSupport: Boolean)

    external fun getCpuFeatures(): Long
}
