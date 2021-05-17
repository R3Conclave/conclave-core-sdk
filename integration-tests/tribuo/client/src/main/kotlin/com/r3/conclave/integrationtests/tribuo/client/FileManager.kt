package com.r3.conclave.integrationtests.tribuo.client

import com.r3.conclave.integrationtests.tribuo.common.DeleteFile
import com.r3.conclave.integrationtests.tribuo.common.EnclaveFile
import java.nio.file.Files
import java.nio.file.Paths

/**
 * This class is responsible for sending file handling requests to the enclave, when not in mock mode.
 */
open class FileManager(protected val client: Client) {
    /**
     * Data directory on the host on which to resolve the file paths.
     */
    val dataDir: String = System.getProperty("dataDir")

    /**
     * Send a file to the enclave.
     * @param filePath file path relative to the [dataDir].
     * @param contentModifier ignored. Only used in [MockFileManager].
     * @return file path in the enclave.
     */
    open fun sendFile(filePath: String, contentModifier: ((ByteArray) -> ByteArray)? = null): String {
        val path = Paths.get(dataDir).resolve(filePath)
        val enclavePath = resolve(filePath)
        val data = Files.readAllBytes(path)
        return client.sendAndReceive(EnclaveFile(enclavePath, data))
    }

    /**
     * Delete a file from the enclave.
     * @param filePath enclave file path.
     * @param contentModifier ignore. Only used in [MockFileManager].
     */
    open fun deleteFile(filePath: String, contentModifier: ((ByteArray) -> ByteArray)? = null) {
        client.sendAndReceive<String>(DeleteFile(filePath))
    }

    /**
     * Resolves the file path in the enclave.
     * @param filePath file path.
     * @return file path in the enclave.
     */
    open fun resolve(filePath: String): String {
        return "/$filePath"
    }
}

/**
 * This class is responsible for the file handling in mock mode. It relies on the host's file system.
 * The content modifiers are useful when the files refer to enclave file paths which need to be adjusted
 * to reflect the host's file paths when running in mock mode.
 */
class MockFileManager(client: Client): FileManager(client) {
    /**
     * Modifies the host's file content if [contentModifier] is not null.
     * @param filePath file path.
     * @param contentModifier function to modify the file content.
     * @return The host's absolute file path.
     */
    override fun sendFile(filePath: String, contentModifier: ((ByteArray) -> ByteArray)?): String {
        val path = Paths.get(resolve(filePath))
        if (contentModifier != null) {
            val originalContent = Files.readAllBytes(path)
            val newContentWithAdjustedPaths = contentModifier(originalContent)
            Files.write(path, newContentWithAdjustedPaths)
        }
        return path.toAbsolutePath().toString()
    }

    /**
     * Modifies the host's file content if [contentModifier] is not null.
     * @param filePath file path.
     * @param contentModifier function to modify the file content.
     */
    override fun deleteFile(filePath: String, contentModifier: ((ByteArray) -> ByteArray)?) {
        if (contentModifier != null) {
            val path = Paths.get(resolve(filePath))
            val originalContent = Files.readAllBytes(path)
            val newContentWithAdjustedPaths = contentModifier(originalContent)
            Files.write(path, newContentWithAdjustedPaths)
        }
    }

    /**
     * Resolves the file path in the [dataDir].
     * @param filePath file path.
     * @return the absolute path to [filePath] in [dataDir].
     */
    override fun resolve(filePath: String): String {
        return Paths.get(dataDir).resolve(filePath).toAbsolutePath().toString()
    }
}