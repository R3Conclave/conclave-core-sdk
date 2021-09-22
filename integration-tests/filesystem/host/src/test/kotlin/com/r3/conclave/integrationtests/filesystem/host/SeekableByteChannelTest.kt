package com.r3.conclave.integrationtests.filesystem.host

import com.r3.conclave.integrationtests.filesystem.host.FileInputStreamTest.Companion.fileInputStreamNonExistingFile
import com.r3.conclave.integrationtests.filesystem.common.proto.Request
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test

class SeekableByteChannelTest : FileSystemEnclaveTest() {

    private class Handler(private val uid: Int, path: String) : AutoCloseable {
        init {
            request(type = Request.Type.BYTE_CHANNEL_DELETE_ON_CLOSE, uid = uid, path = path)
        }

        override fun close() {
            val reply = request(type = Request.Type.BYTE_CHANNEL_CLOSE, uid = uid)
            assertThat(reply).isEmpty()
        }
    }

    @Test
    fun byteChannelDeleteOnClose() {
        val path = "/bytechannel-delete-on-close.data"
        Handler(uid.getAndIncrement(), path).close()
        fileInputStreamNonExistingFile(path)
    }
}
