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
    constructor() : this(FILESYSTEM_ENCLAVE_CLASS_NAME)

    companion object {
        const val FILESYSTEM_ENCLAVE_CLASS_NAME =
            "com.r3.conclave.integrationtests.general.persistingenclave.PersistingEnclave"
    }

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

    fun walkPath(path: String): String {
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
                .cause.hasMessageContaining(oldPath)
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
                .cause.hasMessageContaining(path)
        } else {
            assertThat(callEnclave(DeleteFile(path, false))).isFalse
        }
    }

    fun filesDeleteNonEmptyDir(path: String, nioApi: Boolean) {
        if (nioApi) {
            assertThatThrownBy { callEnclave(DeleteFile(path, true)) }
                .isInstanceOf(RuntimeException::class.java)
                .hasCauseExactlyInstanceOf(DirectoryNotEmptyException::class.java)
                .cause.hasMessageContaining(path)
        } else {
            assertThat(callEnclave(DeleteFile(path, false))).isFalse
        }
    }

    fun createDirectoryWithoutParent(path: String) {
        assertThatThrownBy { callEnclave(FilesCreateDirectory(path)) }
            .isInstanceOf(RuntimeException::class.java)
            .hasCauseInstanceOf(IOException::class.java)
            .cause.hasMessageContaining(path)
    }

    fun createDirectory(path: String) {
        callEnclave(FilesCreateDirectory(path))
    }

    fun createDirectories(path: String) {
        callEnclave(FilesCreateDirectories(path))
    }

    fun movePath(src: String, dst: String) {
        callEnclave(MovePath(src, dst))
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
            .cause.hasMessageContaining(path)
    }

    fun createSymlink(symlinkPath: String, filePath: String) {
        assertThatThrownBy {
            callEnclave(CreateSymlink(symlinkPath, filePath))
        }
            .isInstanceOf(RuntimeException::class.java)
            .hasCauseExactlyInstanceOf(FileSystemException::class.java)
    }

    fun createHardlink(symlinkPath: String, filePath: String) {
        assertThatThrownBy {
            callEnclave(CreateSymlink(symlinkPath, filePath))
        }
            .isInstanceOf(RuntimeException::class.java)
            .hasCauseExactlyInstanceOf(FileSystemException::class.java)
    }
}
