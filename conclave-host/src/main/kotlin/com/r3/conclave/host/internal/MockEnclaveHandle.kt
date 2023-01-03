package com.r3.conclave.host.internal

import com.r3.conclave.common.EnclaveMode
import com.r3.conclave.common.internal.MockCallInterfaceConnector
import com.r3.conclave.common.MockConfiguration
import com.r3.conclave.common.internal.kds.EnclaveKdsConfig
import com.r3.conclave.utilities.internal.EnclaveContext
import com.r3.conclave.common.internal.ThreadLocalEnclaveContext
import com.r3.conclave.host.AttestationParameters
import com.r3.conclave.host.internal.attestation.EnclaveQuoteService
import com.r3.conclave.host.internal.attestation.EnclaveQuoteServiceMock
import java.lang.reflect.InvocationTargetException

class MockEnclaveHandle(
        override val mockEnclave: Any,
        private val mockConfiguration: MockConfiguration?,
        private val kdsConfig: EnclaveKdsConfig?
) : EnclaveHandle {

    override val enclaveMode: EnclaveMode get() = EnclaveMode.MOCK

    override val enclaveClassName: String get() = mockEnclave.javaClass.name

    private val callInterfaceConnector = MockCallInterfaceConnector()

    override val enclaveInterface = MockHostEnclaveInterface(callInterfaceConnector)

    override var quotingService: EnclaveQuoteService = EnclaveQuoteServiceMock

    override fun initialise(attestationParameters: AttestationParameters?) {
        try {
            // The Enclave class will only be on the class path for Mock enclaves and we do not want to add
            // a dependency to the Enclave package on the host so we must lookup the class from its name.
            val enclaveClazz = Class.forName("com.r3.conclave.enclave.Enclave", true, mockEnclave.javaClass.classLoader)

            val initialiseMockMethod = enclaveClazz.getDeclaredMethod(
                    "initialiseMock",
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
                initialiseMockMethod.invoke(mockEnclave, mockConfiguration, kdsConfig, callInterfaceConnector)
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

