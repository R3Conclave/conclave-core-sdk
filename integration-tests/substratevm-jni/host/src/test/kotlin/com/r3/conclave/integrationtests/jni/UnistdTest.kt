package com.r3.conclave.integrationtests.jni

import com.r3.conclave.integrationtests.jni.JniTest.Companion.sendMessage
import com.r3.conclave.integrationtests.jni.tasks.Close
import com.r3.conclave.integrationtests.jni.tasks.Write
import org.assertj.core.api.Assertions.assertThat

class UnistdTest {
    companion object {
        fun write(fd: Int, buf: ByteArray, size: Int) {
            val writeMessage = Write(fd, buf, size.toLong())
            val written = sendMessage(writeMessage)
            assertThat(written).isEqualTo(buf.size.toLong())
        }

        fun close(fildes: Int) {
            val closeMessage = Close(fildes)
            val ret = sendMessage(closeMessage)
            assertThat(ret).isZero
        }
    }
}