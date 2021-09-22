package com.r3.conclave.integrationtests.filesystem.host

import com.r3.conclave.integrationtests.filesystem.common.proto.Request
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class MultiThreadedFileSystemTest : FileSystemEnclaveTest() {

    private fun verifyResponse(reply : ByteArray) {
        val numThreads = reply!![0].toInt()
        val replyBody = reply.sliceArray(1 until reply.size)
        val builder = StringBuilder()

        for (i in 0 until numThreads) {
            builder.append("Dummy text from thread $i\n")
        }
        val replyText = String(replyBody)
        val expectedText = builder.toString();
        assertThat(replyText).isEqualTo(expectedText)
    }

    @Test
    fun readWriteManyFiles () {
        val reply = request(type = Request.Type.MANY_FILES_READ_WRITE)
        verifyResponse(reply!!)
    }

    @Test
    fun multiThreadReadWriteManyFiles () {
        val reply = request(type = Request.Type.MULTI_THREAD_MANY_FILES_READ_WRITE)
        verifyResponse(reply!!)
    }

    @Test
    fun multiThreadReadWriteSingleFile () {
        val reply = request(type = Request.Type.MULTI_THREAD_SINGLE_FILE_READ_WRITE)
        val numThreads = reply!![0].toInt()
        val replyBody = reply.sliceArray(1 until reply.size)
        val replyText = String(replyBody)
        val count = replyText.split("Dummy text from thread").size - 1
        assertThat(count).isEqualTo(numThreads)
    }
}
