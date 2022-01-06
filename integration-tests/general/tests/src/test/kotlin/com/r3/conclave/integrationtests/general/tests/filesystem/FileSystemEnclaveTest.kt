package com.r3.conclave.integrationtests.general.tests.filesystem

import com.r3.conclave.integrationtests.general.common.tasks.*
import com.r3.conclave.integrationtests.general.commontest.AbstractEnclaveActionTest
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import java.io.IOException
import java.nio.file.DirectoryNotEmptyException
import java.nio.file.NoSuchFileException
import java.util.concurrent.atomic.AtomicInteger

// TODO The file system tests should test for both persisting and in-memory scenerios.
abstract class FileSystemEnclaveTest(defaultEnclaveClassName: String) :
    AbstractEnclaveActionTest(defaultEnclaveClassName) {
    constructor() : this("com.r3.conclave.integrationtests.general.persistingenclave.PersistingEnclave")

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

    private fun callEnclaveRename(oldPath: String, newPath: String, nioApi: Boolean) {

        if (nioApi) {
            callEnclave(MovePath(oldPath, newPath))
        } else {
            callEnclave(RenameFile(oldPath, newPath))
        }
    }

    fun renamePath(oldPath: String, newPath: String, nioApi: Boolean) {
        callEnclaveRename(oldPath, newPath, nioApi)
        assertThat(callEnclave(FilesExists(oldPath))).isFalse
        assertThat(callEnclave(FilesExists(newPath))).isTrue
    }

    fun renamePathAcrossFileSystems(oldPath: String, newPath: String, nioApi: Boolean) {
        callEnclaveRename(oldPath, newPath, nioApi)

        if (nioApi) {
            // When using Files.move, the renaming of files succeeds
            //   as the JDK does a copy/delete when the "rename" Posix call fails.
            assertThat(callEnclave(FilesExists(oldPath))).isFalse
            assertThat(callEnclave(FilesExists(newPath))).isTrue
        } else {
            assertThat(callEnclave(FilesExists(oldPath))).isTrue
            assertThat(callEnclave(FilesExists(newPath))).isFalse
        }
    }

    fun renameSamePath(oldPath: String, newPath: String, nioApi: Boolean) {
        callEnclaveRename(oldPath, newPath, nioApi)
        assertThat(callEnclave(FilesExists(oldPath))).isTrue
        assertThat(callEnclave(FilesExists(newPath))).isTrue
    }

    fun listFiles(path: String): String {
        return callEnclave(WalkPath(path))
    }

    fun renameToExistentPath(oldPath: String, newPath: String, nioApi: Boolean) {
        callEnclaveRename(oldPath, newPath, nioApi)
        assertThat(callEnclave(FilesExists(oldPath))).isTrue
        assertThat(callEnclave(FilesExists(newPath))).isTrue
    }

    fun renameNonExistentPath(oldPath: String, newPath: String, nioApi: Boolean) {

        if (nioApi) {
            assertThatThrownBy { callEnclaveRename(oldPath, newPath, true) }
                .isInstanceOf(RuntimeException::class.java)
                .hasCauseExactlyInstanceOf(NoSuchFileException::class.java)
                .hasMessageContaining(oldPath)
        } else {
            callEnclaveRename(oldPath, newPath, false)
        }
        assertThat(callEnclave(FilesExists(oldPath))).isFalse
        assertThat(callEnclave(FilesExists(newPath))).isFalse
    }

    fun filesDeleteNonExistingFile(path: String, nioApi: Boolean) {
        if (nioApi) {
            assertThatThrownBy { callEnclave(DeleteFile(path, true)) }
                .isInstanceOf(RuntimeException::class.java)
                .hasCauseExactlyInstanceOf(NoSuchFileException::class.java)
                .hasMessageContaining(path)
        } else {
            assertThat(callEnclave(DeleteFile(path, false))).isFalse
        }
    }

    fun filesDeleteNonEmptyDir(path: String, nioApi: Boolean) {
        if (nioApi) {
            assertThatThrownBy { callEnclave(DeleteFile(path, true)) }
                .isInstanceOf(RuntimeException::class.java)
                .hasCauseExactlyInstanceOf(DirectoryNotEmptyException::class.java)
                .hasMessageContaining(path)
        } else {
            assertThat(callEnclave(DeleteFile(path, false))).isFalse
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
