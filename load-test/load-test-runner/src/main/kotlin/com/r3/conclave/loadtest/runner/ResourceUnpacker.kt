package com.r3.conclave.loadtest.runner

import java.io.File
import java.io.FileInputStream
import java.net.URL
import java.nio.file.*

class ResourceUnpacker(
        private val classLoader: ClassLoader,
        private val resourceDirectoryPath: String,
        private val destinationDirectory: File
) {

    /**
     * @return the list of unpacked files
     */
    fun unpack(): List<File> {
        val resourceLayouts = classLoader.getResources(resourceDirectoryPath).toList().map { url ->
            getResourceLayout(url, resourceDirectoryPath)
        }

        return resourceLayouts.flatMap { unpackResourceLayout(classLoader, destinationDirectory, it) }
    }

    private data class ResourceLayout(
            val rootPath: Path,
            val directoryPaths: List<Path>,
            val filePaths: List<Path>
    )

    companion object {
        private fun unpackResourceLayout(classLoader: ClassLoader, destinationDirectory: File, resourceLayout: ResourceLayout): List<File> {
            val destinationDirectoryPath = destinationDirectory.toPath()
            destinationDirectory.mkdirs()
            for (path in resourceLayout.directoryPaths) {
                val relativeDirectory = resourceLayout.rootPath.relativize(path)
                val directoryFile = destinationDirectoryPath.resolve(relativeDirectory.toString()).toFile()
                directoryFile.mkdirs()
            }

            val resourcePathToDestination = resourceLayout.filePaths.map { path ->
                val relativeFile = resourceLayout.rootPath.relativize(path)
                val destination = destinationDirectoryPath.resolve(relativeFile.toString())
                Pair(path.toString(), destination)
            }

            resourcePathToDestination.parallelStream().forEach { (path, destination) ->
                val fileStream = classLoader.getResourceAsStream(path.drop(1)) ?: FileInputStream(path)
                fileStream.use {
                    Files.copy(it, destination, StandardCopyOption.REPLACE_EXISTING)
                }
            }

            return resourcePathToDestination.map { it.second.toFile() }
        }

        private fun getResourceLayout(resourcesUrl: URL, resourcePath: String): ResourceLayout {
            val fileResourcePaths = ArrayList<Path>()
            val directoryResourcePaths = ArrayList<Path>()
            val resourcesUri = resourcesUrl.toURI()
            when (resourcesUri.scheme) {
                "file" -> {
                    val rootPath = Paths.get(resourcesUri)
                    for (path in Files.walk(rootPath)) {
                        if (path == rootPath) {
                            continue
                        }
                        if (Files.isDirectory(path)) {
                            directoryResourcePaths.add(path)
                        } else {
                            fileResourcePaths.add(path)
                        }
                    }
                    return ResourceLayout(rootPath.toAbsolutePath(), directoryResourcePaths, fileResourcePaths)
                }
                "jar", "zip" -> {
                    return FileSystems.newFileSystem(resourcesUri, emptyMap<String, Any?>()).use {
                        val rootPath = it.getPath(resourcePath)
                        for (path in Files.walk(rootPath)) {
                            if (path == rootPath) {
                                continue
                            }
                            if (Files.isDirectory(path)) {
                                directoryResourcePaths.add(path)
                            } else {
                                fileResourcePaths.add(path)
                            }
                        }
                        ResourceLayout(rootPath.toAbsolutePath(), directoryResourcePaths, fileResourcePaths)
                    }
                }
                else -> {
                    throw IllegalArgumentException("Unrecognized scheme ${resourcesUri.scheme}")
                }
            }

        }

    }
}