package com.r3.conclave.integrationtests.filesystem.host

import com.google.protobuf.Int32Value
import com.r3.conclave.integrationtests.filesystem.common.proto.Request
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.io.BufferedInputStream

class BufferedInputStreamTest : FileSystemEnclaveTest() {

    private class Handler(private val uid: Int, path: String) : AutoCloseable {
        init {
            val reply = request(type = Request.Type.BUFFERED_INPUT_STREAM_OPEN,
                    uid = uid,
                    path = path)
            assertThat(String(reply!!)).startsWith(BufferedInputStream::class.java.name + "@")
        }

        override fun close() {
            val reply = request(type = Request.Type.INPUT_STREAM_CLOSE, uid = uid)
            assertThat(reply).isEmpty()
        }

        fun readBytes(bufSize: Int) {
            var read = 0
            while (read >= 0) {
                val reply = request(type = Request.Type.BUFFERED_INPUT_STREAM_READ_BYTES,
                        uid = uid,
                        length = bufSize)
                read = Int32Value.parseFrom(reply).value
            }
        }
    }

    @Test
    fun readToBuffer() {
        val data = ByteArray(10 * 1024 * 1024) { i -> i.toByte() }
        val path = "/buffered-input-stream.data"
        FilesTest.filesWrite(path, data)

        Handler(uid.getAndIncrement(), path).use {
            it.readBytes(16 * 1024)
        }
    }
}