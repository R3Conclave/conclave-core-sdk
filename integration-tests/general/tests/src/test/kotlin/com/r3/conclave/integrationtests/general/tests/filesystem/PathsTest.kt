package com.r3.conclave.integrationtests.general.tests.filesystem

import com.r3.conclave.integrationtests.general.common.tasks.PathsGet
import com.r3.conclave.integrationtests.general.commontest.TestUtils.graalvmOnlyTest
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

class PathsTest : FileSystemEnclaveTest() {

    @ParameterizedTest
    @CsvSource(
        "/paths.data, true",
        "/paths.data, false",
        "/tmp/paths.data, true",
        "/tmp/paths.data, false"
    )
    fun pathsGet(path: String, nioApi: Boolean) {
        graalvmOnlyTest() // CON-1264: Gramine: accessing filesystem and devices causes InvalidKeyException: Invalid AES key length: 0 bytes
        val fileData = byteArrayOf(1, 2, 3)
        filesWrite(path, fileData)

        callEnclave(PathsGet(path))
        deleteFile(path, nioApi)
    }
    @ParameterizedTest
    @CsvSource(
        "/paths.data, /paths.datalink",
        "/tmp/paths.data, /tmp/paths.datalink",
        "/paths.data, /tmp/paths.datalink",
        "/tmp/paths.data, /paths.datalink"

    )
    fun symlink(path: String, symlinkPath: String) {
        graalvmOnlyTest() // CON-1264: Gramine: accessing filesystem and devices causes InvalidKeyException: Invalid AES key length: 0 bytes
        val fileData = byteArrayOf(1, 2, 3)
        filesWrite(path, fileData)

        createSymlink(symlinkPath, path)
    }

    @ParameterizedTest
    @CsvSource(
        "/paths.data, /paths.datalink",
        "/tmp/paths.data, /tmp/paths.datalink",
        "/paths.data, /tmp/paths.datalink",
        "/tmp/paths.data, /paths.datalink"
    )
    fun link(path: String, symlinkPath: String) {
        graalvmOnlyTest() // CON-1264: Gramine: accessing filesystem and devices causes InvalidKeyException: Invalid AES key length: 0 bytes
        val fileData = byteArrayOf(1, 2, 3)
        filesWrite(path, fileData)

        createHardlink(symlinkPath, path)
    }
}
