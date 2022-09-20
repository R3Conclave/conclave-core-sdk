package com.r3.conclave.host.internal

import com.r3.conclave.common.EnclaveMode
import com.r3.conclave.common.MockCallInterfaceConnector
import com.r3.conclave.common.MockConfiguration
import com.r3.conclave.common.internal.handler.Handler
import com.r3.conclave.common.internal.handler.HandlerConnected
import com.r3.conclave.common.internal.handler.LeafSender
import com.r3.conclave.common.internal.handler.Sender
import com.r3.conclave.common.internal.kds.EnclaveKdsConfig
import com.r3.conclave.utilities.internal.EnclaveContext
import com.r3.conclave.common.internal.ThreadLocalEnclaveContext
import java.lang.reflect.InvocationTargetException
import java.nio.ByteBuffer

class MockEnclaveHandle<CONNECTION>(
        override val mockEnclave: Any,
        private val mockConfiguration: MockConfiguration?,
        private val kdsConfig: EnclaveKdsConfig?,
        private val hostHandler: Handler<CONNECTION>
) : EnclaveHandle<CONNECTION>, LeafSender() {

    override val enclaveMode: EnclaveMode get() = EnclaveMode.MOCK

    override val connection: CONNECTION = hostHandler.connect(this)

    override val enclaveClassName: String get() = mockEnclave.javaClass.name

    private val callInterfaceConnector = MockCallInterfaceConnector()

    override val enclaveCallInterface = run {
        val enclaveCallInterface = MockEnclaveCallInterface(callInterfaceConnector)
        callInterfaceConnector.setHostCallAcceptor(enclaveCallInterface)
        enclaveCallInterface
    }

    private lateinit var enclaveHandler: HandlerConnected<*>

    override fun sendSerialized(serializedBuffer: ByteBuffer) {
        check(!EnclaveContext.isInsideEnclave())
        ThreadLocalEnclaveContext.set(true)
        try {
            enclaveHandler.onReceive(serializedBuffer)
        } finally {
            ThreadLocalEnclaveContext.set(false)
        }
    }

    override fun initialise() {
        val sender = MockOcallSender(HandlerConnected(hostHandler, connection))
        try {
            // The Enclave class will only be on the class path for Mock enclaves and we do not want to add
            // a dependency to the Enclave package on the host so we must lookup the class from its name.
            val enclaveClazz = Class.forName("com.r3.conclave.enclave.Enclave", true, mockEnclave.javaClass.classLoader)

            val initialiseMockMethod = enclaveClazz.getDeclaredMethod(
                    "initialiseMock",
                    Sender::class.java,
                    MockConfiguration::class.java,
                    EnclaveKdsConfig::class.java,
                    MockCallInterfaceConnector::class.java
            )
            initialiseMockMethod.isAccessible = true

            EnclaveContext.Companion::class.java.getDeclaredField("instance").apply { isAccessible = true }
                    .set(null, ThreadLocalEnclaveContext)

            check(!EnclaveContext.isInsideEnclave())
            ThreadLocalEnclaveContext.set(true)
            try {
                enclaveHandler = initialiseMockMethod.invoke(mockEnclave, sender, mockConfiguration, kdsConfig, callInterfaceConnector) as HandlerConnected<*>
            } finally {
                ThreadLocalEnclaveContext.set(false)
            }
        } catch (e: InvocationTargetException) {
            throw e.cause ?: e
        }
    }

    override fun destroy() {
    }
}

