package com.r3.conclave.integrationtests.general.tests.filesystem

import com.r3.conclave.integrationtests.general.common.tasks.PathsGet
import org.junit.jupiter.api.Test

class PathsTest : FileSystemEnclaveTest() {
    @Test
    fun pathsGet() {
        val path = "/paths.data"
        val fileData = byteArrayOf(1, 2, 3)
        filesWrite(path, fileData)

        callEnclave(PathsGet(path))
        filesDelete(path)
    }
}
