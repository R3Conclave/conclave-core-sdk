package com.r3.conclave.integrationtests.general.tests.filesystem

import com.r3.conclave.integrationtests.general.common.tasks.*
import com.r3.conclave.integrationtests.general.commontest.AbstractEnclaveActionTest
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import java.io.IOException
import java.nio.file.DirectoryNotEmptyException
import java.nio.file.NoSuchFileException
import java.util.concurrent.atomic.AtomicInteger

abstract class FileSystemEnclaveTest(defaultEnclaveClassName: String) : AbstractEnclaveActionTest(defaultEnclaveClassName) {
    constructor() : this("com.r3.conclave.integrationtests.filesystem.enclave.PersistentFileSystemEnclave")

    val uid = AtomicInteger()

    fun filesWrite(path: String, data: ByteArray) {
        callEnclave(FilesWrite(path, data))
    }

    fun filesReadAllBytes(path: String, data: ByteArray) {
        val reply = callEnclave(FilesReadAllBytes(path))
        assertThat(reply).isEqualTo(data)
    }

    fun deleteFile(path: String, nioApi: Boolean) {
        callEnclave(DeleteFile(path, nioApi))
        assertThat(callEnclave(FilesExists(path))).isFalse
    }

    fun filesDeleteNonExistingFile(path: String, nioApi: Boolean) {
        if (nioApi) {
            assertThatThrownBy { callEnclave(DeleteFile(path, nioApi)) }
                .isInstanceOf(RuntimeException::class.java)
                .hasCauseExactlyInstanceOf(NoSuchFileException::class.java)
                .hasMessageContaining(path)
        } else {
            assertThat(callEnclave(DeleteFile(path, nioApi))).isFalse
        }
    }

    fun filesDeleteNonEmptyDir(path: String, nioApi: Boolean) {
        if (nioApi) {
            assertThatThrownBy { callEnclave(DeleteFile(path, nioApi)) }
                .isInstanceOf(RuntimeException::class.java)
                .hasCauseExactlyInstanceOf(DirectoryNotEmptyException::class.java)
                .hasMessageContaining(path)
        } else {
            assertThat(callEnclave(DeleteFile(path, nioApi))).isFalse
        }
    }

    fun createDirectoryWithoutParent(path: String) {
        assertThatThrownBy { callEnclave(FilesCreateDirectory(path)) }
            .isInstanceOf(RuntimeException::class.java)
            .hasCauseInstanceOf(IOException::class.java)
            .hasMessageContaining(path)
    }

    fun createDirectory(path: String) {
        callEnclave(FilesCreateDirectory(path))
    }

    fun createDirectories(path: String) {
        callEnclave(FilesCreateDirectories(path))
    }

    fun filesSize(path: String, expectedSize: Long) {
        assertThat(callEnclave(FilesSize(path))).isEqualTo(expectedSize)
    }

    fun fileInputStreamNonExistingFile(path: String, nioApi: Boolean) {
        val exception = if (nioApi) {
            java.nio.file.NoSuchFileException::class.java
        } else {
            java.io.FileNotFoundException::class.java
        }
        assertThatThrownBy {
            callEnclave(
                NewInputStream(
                    path,
                    uid.getAndIncrement(),
                    nioApi
                )
            )
        }
            .isInstanceOf(RuntimeException::class.java)
            .hasCauseExactlyInstanceOf(exception)
            .hasMessageContaining(path)
    }
}
