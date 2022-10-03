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
                    0, 1,2, 3, Random.Default.nextBytes(dataSize))
        }
    }

    @Test
    fun `byte array conversion test`() {
        val initialMessage = randomMessage()
        val initialBytes = initialMessage.toBytes()
        val deserialisedMessage = StreamCallInterfaceMessage.fromBytes(initialBytes)

        assertEqual(initialMessage, deserialisedMessage)
    }

    @Test
    fun `stream read write test`() {
        val initialMessage = randomMessage()
        val bufferSize = initialMessage.size() + 4

        val outputStream = ByteArrayOutputStream(bufferSize)

        initialMessage.writeToStream(outputStream)

        val inputStream = ByteArrayInputStream(outputStream.toByteArray())

        val deserialisedMessage = StreamCallInterfaceMessage.readFromStream(inputStream)

        assertEqual(initialMessage, deserialisedMessage)
    }

    private fun assertEqual(a: StreamCallInterfaceMessage, b: StreamCallInterfaceMessage) {
        assertThat(a.targetThreadID).isEqualTo(b.targetThreadID)
        assertThat(a.sourceThreadID).isEqualTo(b.sourceThreadID)
        assertThat(a.callTypeID).isEqualTo(b.callTypeID)
        assertThat(a.messageTypeID).isEqualTo(b.messageTypeID)
        assertThat(a.payload).isEqualTo(b.payload)
    }
}
