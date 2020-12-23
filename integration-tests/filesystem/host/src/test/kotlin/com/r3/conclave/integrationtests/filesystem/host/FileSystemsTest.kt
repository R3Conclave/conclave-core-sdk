package com.r3.conclave.integrationtests.filesystem.host

import com.r3.conclave.integrationtests.filesystem.common.proto.Request
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class FileSystemsTest : FileSystemEnclaveTest() {

    @Test
    fun getDefault() {
        val reply = request(type = Request.Type.FILE_SYSTEMS_GET_DEFAULT)
        assertThat(String(reply!!)).startsWith("com.r3.conclave.filesystem.jimfs.JimfsFileSystem@")
    }

    @Test
    fun getDefaultProvider() {
        val reply = request(type = Request.Type.FILE_SYSTEMS_GET_DEFAULT_PROVIDER)
        assertThat(String(reply!!)).startsWith("com.r3.conclave.filesystem.jimfs.JimfsFileSystemProvider@")
    }
}