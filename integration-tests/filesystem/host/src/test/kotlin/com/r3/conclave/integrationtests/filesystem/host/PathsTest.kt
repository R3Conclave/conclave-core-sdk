package com.r3.conclave.integrationtests.filesystem.host

import com.r3.conclave.integrationtests.filesystem.host.FilesTest.Companion.filesDelete
import com.r3.conclave.integrationtests.filesystem.host.FilesTest.Companion.filesWrite
import com.r3.conclave.integrationtests.filesystem.common.proto.Request
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class PathsTest : FileSystemEnclaveTest() {

    @Test
    fun pathsGet() {
        val path = "/paths.data"
        val fileData = byteArrayOf(1, 2, 3)
        filesWrite(path, fileData)

        val reply = request(type = Request.Type.PATHS_GET, path = path)
        assertThat(String(reply!!)).isEqualTo(path)
        filesDelete(path)
    }
}