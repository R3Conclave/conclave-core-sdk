package com.r3.sgx.core.host.internal

import io.github.classgraph.ClassGraph
import java.nio.file.Files
import java.nio.file.Path

/**
 * The JNI interface of the host shared between Simulation, Release and Debug enclaves. Requires symbols loaded with [NativeLoaderShared]
 */
object NativeShared {
    
    init {
        loadHostLibraries()
    }

    private fun loadHostLibraries() {
        val tempDirectory = createTempDirectory()
        val hostLibrariesResourcePath = "com/r3/sgx/host-libraries/shared"

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

        System.load(tempDirectory.resolve("libjvm_host_shared.so").toAbsolutePath().toString())
    }

    private fun createTempDirectory(): Path {
        val tempDirectory = Files.createTempDirectory("com.r3.conclave.host-libraries-shared")
        Runtime.getRuntime().addShutdownHook(Thread { tempDirectory.toFile().deleteRecursively() })
        return tempDirectory
    }

    external fun checkPlatformSupportsEnclaves(enableSupport: Boolean)
}
