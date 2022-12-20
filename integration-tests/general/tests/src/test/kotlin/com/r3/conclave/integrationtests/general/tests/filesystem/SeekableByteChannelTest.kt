package com.r3.conclave.integrationtests.general.tests.filesystem

import com.r3.conclave.integrationtests.general.common.tasks.CloseByteChannel
import com.r3.conclave.integrationtests.general.common.tasks.NewDeleteOnCloseByteChannel
import com.r3.conclave.integrationtests.general.commontest.TestUtils.graalvmOnlyTest
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
        graalvmOnlyTest() // CON-1264: Gramine: accessing filesystem and devices causes InvalidKeyException: Invalid AES key length: 0 bytes
        Handler(uid.getAndIncrement(), path).close()
        fileInputStreamNonExistingFile(path, nioApi)
    }
}
