package com.r3.conclave.integrationtests.general.tests.filesystem

import com.r3.conclave.integrationtests.general.common.tasks.PathsGet
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

class PathsTest : FileSystemEnclaveTest() {
    @ParameterizedTest
    @ValueSource(booleans = [true, false])
    fun pathsGet(nioApi: Boolean) {
        val path = "/paths.data"
        val fileData = byteArrayOf(1, 2, 3)
        filesWrite(path, fileData)

        callEnclave(PathsGet(path))
        deleteFile(path, nioApi)
    }
}
