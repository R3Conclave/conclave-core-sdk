package com.r3.conclave.integrationtests.general.tests.filesystem

import com.r3.conclave.integrationtests.general.common.tasks.CloseByteChannel
import com.r3.conclave.integrationtests.general.common.tasks.NewDeleteOnCloseByteChannel
import org.junit.jupiter.api.Test

class SeekableByteChannelTest : FileSystemEnclaveTest() {

    private inner class Handler(private val uid: Int, path: String) : AutoCloseable {
        init {
            callEnclave(NewDeleteOnCloseByteChannel(path, uid))
        }

        override fun close() {
            callEnclave(CloseByteChannel(uid))
        }
    }

    @Test
    fun byteChannelDeleteOnClose() {
        val path = "/bytechannel-delete-on-close.data"
        Handler(uid.getAndIncrement(), path).close()
        fileInputStreamNonExistingFile(path)
    }
}
