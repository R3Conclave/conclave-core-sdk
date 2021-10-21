package com.r3.conclave.integrationtests.general.tests.filesystem

import com.r3.conclave.integrationtests.general.common.tasks.CloseByteChannel
import com.r3.conclave.integrationtests.general.common.tasks.NewDeleteOnCloseByteChannel
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

class SeekableByteChannelTest : FileSystemEnclaveTest() {

    private inner class Handler(private val uid: Int, path: String) : AutoCloseable {
        init {
            callEnclave(NewDeleteOnCloseByteChannel(path, uid))
        }

        override fun close() {
            callEnclave(CloseByteChannel(uid))
        }
    }

    @ParameterizedTest
    @CsvSource(
        "/bytechannel-delete-on-close.data, true",
        "/bytechannel-delete-on-close.data, false",
        "/tmp/bytechannel-delete-on-close.data, true",
        "/tmp/bytechannel-delete-on-close.data, false"
    )
    fun byteChannelDeleteOnClose(path: String, nioApi: Boolean) {
        Handler(uid.getAndIncrement(), path).close()
        fileInputStreamNonExistingFile(path, nioApi)
    }
}
