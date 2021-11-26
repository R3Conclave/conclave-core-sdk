package com.r3.conclave.host.internal

import com.r3.conclave.common.EnclaveMode
import com.r3.conclave.common.MockConfiguration
import com.r3.conclave.common.internal.handler.Handler
import com.r3.conclave.common.internal.handler.HandlerConnected
import com.r3.conclave.common.internal.handler.LeafSender
import com.r3.conclave.common.internal.handler.Sender
import com.r3.conclave.utilities.internal.EnclaveContext
import java.lang.reflect.InvocationTargetException
import java.nio.ByteBuffer
import java.util.*

class MockEnclaveHandle<CONNECTION>(
        override val mockEnclave: Any,
        private val mockConfiguration: MockConfiguration?,
        private val enclavePropertiesOverride: Properties?,
        hostHandler: Handler<CONNECTION>
) : EnclaveHandle<CONNECTION>, LeafSender() {

    override val enclaveMode: EnclaveMode get() = EnclaveMode.MOCK

    override val connection: CONNECTION = hostHandler.connect(this)

    override val enclaveClassName: String get() = mockEnclave.javaClass.name

    private val enclaveHandler by lazy {
        val sender = MockOcallSender(HandlerConnected(hostHandler, connection))
        try {
            // The Enclave class will only be on the class path for Mock enclaves and we do not want to add
            // a dependency to the Enclave package on the host so we must lookup the class from its name.
            val enclaveClazz = Class.forName("com.r3.conclave.enclave.Enclave", true, mockEnclave.javaClass.classLoader)

            val initialiseMethod =
                    enclaveClazz.getDeclaredMethod("initialiseMock", Sender::class.java, MockConfiguration::class.java, Properties::class.java)
                            .apply { isAccessible = true }

            EnclaveContext.Companion::class.java.getDeclaredField("instance").apply { isAccessible = true }
                    .set(null, ThreadLocalEnclaveContext)

            initialiseMethod.invoke(
                    mockEnclave, sender, mockConfiguration, enclavePropertiesOverride
            ) as HandlerConnected<*>
        } catch (e: InvocationTargetException) {
            throw e.cause ?: e
        }
    }

    override fun sendSerialized(serializedBuffer: ByteBuffer) {
        check(!EnclaveContext.isInsideEnclave())
        ThreadLocalEnclaveContext.set(true)
        try {
            enclaveHandler.onReceive(serializedBuffer)
        } finally {
            ThreadLocalEnclaveContext.set(false)
        }
    }

    override fun destroy() {
    }
}

