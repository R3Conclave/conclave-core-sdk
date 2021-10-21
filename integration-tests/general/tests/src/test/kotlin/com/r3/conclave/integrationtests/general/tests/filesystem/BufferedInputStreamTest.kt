package com.r3.conclave.integrationtests.general.tests.filesystem

import com.r3.conclave.integrationtests.general.common.tasks.CloseInputStream
import com.r3.conclave.integrationtests.general.common.tasks.NewBufferedFileInputStream
import com.r3.conclave.integrationtests.general.common.tasks.ReadBytesFromInputStream
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import java.io.BufferedInputStream

class BufferedInputStreamTest : FileSystemEnclaveTest() {

    private inner class Handler(private val uid: Int, path: String) : AutoCloseable {
        init {
            val reply = callEnclave(NewBufferedFileInputStream(path, uid))
            assertThat(reply).startsWith(BufferedInputStream::class.java.name + "@")
        }

        override fun close() {
            callEnclave(CloseInputStream(uid))
        }

        fun readBytes(bufSize: Int) {
            var read = 0
            while (read >= 0) {
                read = callEnclave(ReadBytesFromInputStream(uid, bufSize))
            }
        }
    }

    @ParameterizedTest
    @ValueSource(strings = ["/buffered-input-stream.data", "/tmp/buffered-input-stream.data"])
    fun readToBuffer(path: String) {
        val data = ByteArray(10 * 1024 * 1024) { i -> i.toByte() }
        filesWrite(path, data)

        Handler(uid.getAndIncrement(), path).use {
            it.readBytes(16 * 1024)
        }
    }
}