package com.r3.conclave.integrationtests.general.tests.filesystem

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import java.nio.file.Paths


class RenameTests : FileSystemEnclaveTest() {

    @ParameterizedTest
    @CsvSource(
        "/file1.data, /file2.data, true",
        "/file1.data, /file2.data, false",
        "/tmp/file1.data, /tmp/file2.data, true",
        "/tmp/file1.data, /tmp/file2.data, false"
    )
    fun renameFiles(oldPath: String, newPath: String, nioApi: Boolean) {
        val testData = "Test data for a file to rename"
        filesWrite(oldPath, testData.toByteArray())
        renamePath(oldPath, newPath, nioApi)
    }

    @ParameterizedTest
    @CsvSource(
        "/file1.data, /file1.data, true",
        "/file1.data, /file1.data, false",
        "/tmp/file1.data, /tmp/file1.data, true",
        "/tmp/file1.data, /tmp/file1.data, false"
    )
    fun renameFilesToThemselves(oldPath: String, newPath: String, nioApi: Boolean) {
        val testData = "Test data for a file to rename"
        filesWrite(oldPath, testData.toByteArray())
        renameSamePath(oldPath, newPath, nioApi)
    }

    @ParameterizedTest
    @CsvSource(
        "/file1.data, /testdir/file1.data, true",
        "/file1.data, /testdir/file1.data, false",
        "/tmp/file1.data, /tmp/testdir/file1.data, true",
        "/tmp/file1.data, /tmp/testdir/file1.data, false"
    )
    fun renameFilesWithDir(oldPath: String, newPath: String, nioApi: Boolean) {
        val testData = "Test data for a file to rename"
        filesWrite(oldPath, testData.toByteArray())
        createDirectory(Paths.get(newPath).parent.toString())
        renamePath(oldPath, newPath, nioApi)
    }

    @ParameterizedTest
    @CsvSource(
        "/file1.data, /file2.data, true",
        "/file1.data, /file2.data, false",
        "/tmp/file1.data, /tmp/file2.data, true",
        "/tmp/file1.data, /tmp/file2.data, false",
    )
    fun renameNonExistentFiles(oldPath: String, newPath: String, nioApi: Boolean) {
        renameNonExistentPath(oldPath, newPath, nioApi)
    }

    @ParameterizedTest
    @CsvSource(
        "/file1.data, /file2.data, true",
        "/file1.data, /file2.data, false",
        "/tmp/file1.data, /tmp/file2.data, true",
        "/tmp/file1.data, /tmp/file2.data, false"
    )
    fun renameToExistentFiles(oldPath: String, newPath: String, nioApi: Boolean) {
        val testData1 = "Test data for a file to rename"
        val testData2 = "More data for a file to rename, bigger size than previous one"
        filesWrite(oldPath, testData1.toByteArray())
        filesWrite(newPath, testData2.toByteArray())
        renameToExistentPath(oldPath, newPath, nioApi)
        assertThat(walkPath(oldPath)).contains(testData1)
        assertThat(walkPath(newPath)).contains(testData2)
    }

    @ParameterizedTest
    @CsvSource(
        "/emptydir, /emptydir2, true",
        "/emptydir, /emptydir2, false",
        "/tmp/emptydir, /tmp/emptydir2, true",
        "/tmp/emptydir, /tmp/emptydir2, false"
    )
    fun renameEmptyDirectories(oldPath: String, newPath: String, nioApi: Boolean) {
        createDirectory(oldPath)
        renamePath(oldPath, newPath, nioApi)
    }

    @ParameterizedTest
    @CsvSource(
        "/nonemptydir, /nonemptydir2, true",
        "/nonemptydir, /nonemptydir2, false",
        "/tmp/nonemptydir, /tmp/nonemptydir2, true",
        "/tmp/nonemptydir, /tmp/nonemptydir2, false"
    )
    fun renameNonEmptyDirectories(oldPath: String, newPath: String, nioApi: Boolean) {
        val testData = "Test data for a file to rename"
        createDirectory(oldPath)
        filesWrite("$oldPath/test_file.txt", testData.toByteArray())
        renamePath(oldPath, newPath, nioApi)
    }

    @ParameterizedTest
    @CsvSource(
        "/nonemptydir, /nonemptydir2, true",
        "/nonemptydir, /nonemptydir2, false",
        "/tmp/nonemptydir, /tmp/nonemptydir2, true",
        "/tmp/nonemptydir, /tmp/nonemptydir2, false"
    )
    fun renameToNonEmptyDirectories(oldPath: String, newPath: String, nioApi: Boolean) {
        val testData = "Test data for a file to rename"
        createDirectory(oldPath)
        filesWrite("$oldPath/test_file.txt", testData.toByteArray())
        createDirectory(newPath)
        filesWrite("$newPath/test_file.txt", testData.toByteArray())
        renameToExistentPath(oldPath, newPath, nioApi)
    }

    @ParameterizedTest
    @CsvSource(
        "/nonemptydir, /nonemptydir2, true",
        "/nonemptydir, /nonemptydir2, false",
        "/tmp/nonemptydir, /tmp/emptydir2, true",
        "/tmp/nonemptydir, /tmp/emptydir2, false"
    )
    fun renameNestedDirectories(oldPath: String, newPath: String, nioApi: Boolean) {
        val testData1 = "Test data1 for a file to rename"
        val testData2 = "Test data2 for a file to rename"
        createDirectory(oldPath)
        val level1Dir = Paths.get(oldPath).resolve("level1")
        createDirectory(level1Dir.toString())
        filesWrite("$level1Dir/test_file.txt", testData1.toByteArray())
        val level2Dir = level1Dir.resolve("level2")
        createDirectory(level2Dir.toString())
        filesWrite("$level2Dir/test_file.txt", testData2.toByteArray())
        renamePath(oldPath, newPath, nioApi)

        val oldFiles = walkPath(oldPath)
        val newFiles = walkPath(newPath)
        println("Walk through old files")
        println(oldFiles.length)
        println("Walk through new files")
        println(newFiles)

        val newLevel1 = "$level1Dir/test_file.txt".replace(oldPath, newPath)
        val newLevel2 = "$level2Dir/test_file.txt".replace(oldPath, newPath)
        assertThat(oldFiles == "").isTrue

        assertThat(newFiles.contains("Test data1 for a file to rename")).isTrue
        assertThat(newFiles.contains("Test data2 for a file to rename")).isTrue
        assertThat(newFiles.contains(newLevel1)).isTrue
        assertThat(newFiles.contains(newLevel2)).isTrue
    }

    @ParameterizedTest
    @CsvSource(
        "/file1.data, /tmp/file1.data, true",
        "/file1.data, /tmp/file1.data, false",
        "/tmp/file1.data, /file1.data, true",
        "/tmp/file1.data, /file1.data, false"
    )
    fun renameAcrossFileSystems(oldPath: String, newPath: String, nioApi: Boolean) {
        val testData = "Test data for a file to rename"
        filesWrite(oldPath, testData.toByteArray())
        renamePathAcrossFileSystems(oldPath, newPath, nioApi)
    }
}
