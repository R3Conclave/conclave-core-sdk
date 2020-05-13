package com.r3.sgx.core.enclave

import com.r3.sgx.core.common.Handler
import com.r3.sgx.core.common.Sender
import com.r3.sgx.core.common.SimpleMuxingHandler
import com.r3.sgx.dynamictesting.TestEnclavesBasedTest
import com.r3.sgx.testing.EchoEnclave
import com.r3.sgx.testing.StringHandler
import com.r3.sgx.testing.StringSender
import org.assertj.core.api.Assertions.assertThatIllegalStateException
import org.junit.Test
import java.nio.ByteBuffer
import java.util.function.Consumer
import kotlin.test.assertEquals

class EnclaveTest : TestEnclavesBasedTest() {
    @Test
    fun `exceptions are rethrown in enclave`() {
        class ThrowingHandler : StringHandler() {
            override fun onReceive(sender: StringSender, string: String) {
                throw IllegalStateException("Go away!")
            }
        }

        val handler = ThrowingHandler()
        val sender = createEnclave(EchoEnclave::class.java).addDownstream(handler)
        assertThatIllegalStateException().isThrownBy {
            sender.send("")
        }.withMessage("Go away!")
    }

    @Test
    fun `ECALL-OCALL recursion`() {
        val host = RecursingHost()
        val sender = createEnclave(RecursingEnclave::class.java).addDownstream(host)
        sender.send(Int.SIZE_BYTES, Consumer { buffer ->
            buffer.putInt(100)
        })
        assertEquals(50, host.called)
    }

    class RecursingHost : Handler<Sender> {
        var called = 0

        override fun connect(upstream: Sender): Sender = upstream

        override fun onReceive(connection: Sender, input: ByteBuffer) {
            called++
            val remaining = input.getInt()
            if (remaining > 0) {
                connection.send(Int.SIZE_BYTES, Consumer { buffer ->
                    buffer.putInt(remaining.dec())
                })
            }
        }
    }

    class RecursingEnclave : RootEnclave() {
        override fun initialize(api: EnclaveApi, mux: SimpleMuxingHandler.Connection) {
            mux.addDownstream(RecursingEnclaveHandler())
        }

        private class RecursingEnclaveHandler : Handler<Sender> {
            override fun connect(upstream: Sender): Sender = upstream

            override fun onReceive(connection: Sender, input: ByteBuffer) {
                val remaining = input.getInt()
                if (remaining > 0) {
                    connection.send(Int.SIZE_BYTES, Consumer { buffer ->
                        buffer.putInt(remaining.dec())
                    })
                }
            }
        }
    }
}
