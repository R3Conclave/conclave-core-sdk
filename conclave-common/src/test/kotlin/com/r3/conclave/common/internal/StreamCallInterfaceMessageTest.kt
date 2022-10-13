package com.r3.conclave.common.internal

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import kotlin.random.Random

class StreamCallInterfaceMessageTest {
    companion object {
        fun randomMessage(dataSize: Int = 42): StreamCallInterfaceMessage {
            return StreamCallInterfaceMessage(
                    0, 1,2, Random.Default.nextBytes(dataSize))
        }
    }

    @Test
    fun `stream read write test`() {
        val initialMessage = randomMessage()
        val outputStream = ByteArrayOutputStream(1024)

        initialMessage.writeToStream(outputStream)

        val inputStream = ByteArrayInputStream(outputStream.toByteArray())
        val deserialisedMessage = StreamCallInterfaceMessage.readFromStream(inputStream)

        assertEqual(initialMessage, deserialisedMessage)
    }

    private fun assertEqual(a: StreamCallInterfaceMessage, b: StreamCallInterfaceMessage) {
        assertThat(a.hostThreadID).isEqualTo(b.hostThreadID)
        assertThat(a.callTypeID).isEqualTo(b.callTypeID)
        assertThat(a.messageTypeID).isEqualTo(b.messageTypeID)
        assertThat(a.payload).isEqualTo(b.payload)
    }
}
