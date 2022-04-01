package com.r3.conclave.integrationtests.general.tests.filesystem

import com.r3.conclave.integrationtests.general.common.tasks.PathsGet
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
        val fileData = byteArrayOf(1, 2, 3)
        filesWrite(path, fileData)

        callEnclave(PathsGet(path))
        deleteFile(path, nioApi)
    }
}
