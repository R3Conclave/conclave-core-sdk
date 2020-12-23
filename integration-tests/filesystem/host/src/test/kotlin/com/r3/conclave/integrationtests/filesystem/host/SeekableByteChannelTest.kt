package com.r3.conclave.integrationtests.filesystem.host

import com.r3.conclave.integrationtests.filesystem.host.FileInputStreamTest.Companion.fileInputStreamNonExistingFile
import com.r3.conclave.integrationtests.filesystem.common.proto.Request
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test

class SeekableByteChannelTest : FileSystemEnclaveTest() {

    private class Handler(private val uid: Int, path: String) : AutoCloseable {
        init {
            val reply = request(type = Request.Type.BYTE_CHANNEL_DELETE_ON_CLOSE, uid = uid, path = path)
            assertThat(String(reply!!)).contains("com.r3.conclave.filesystem.jimfs.JimfsFileChannel@")
        }

        override fun close() {
            val reply = request(type = Request.Type.BYTE_CHANNEL_CLOSE, uid = uid)
            assertThat(reply).isEmpty()
        }
    }

    @Disabled("DELETE_ON_CLOSE not supported")
    @Test
    fun byteChannelDeleteOnClose() {
        val path = "/bytechannel-delete-on-close.data"
        Handler(uid.getAndIncrement(), path).close()
        fileInputStreamNonExistingFile(path)
    }
}