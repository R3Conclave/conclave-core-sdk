package com.r3.conclave.testing

import com.r3.conclave.enclave.Enclave
import com.r3.conclave.host.EnclaveHost
import com.r3.conclave.testing.internal.MockInternals

/**
 * A fast, purely in-memory mocked out [EnclaveHost]. No actual enclave file is required and no part of the hardware
 * stack is invoked. The purpose of this class is simply to let you whitebox test your own app-level logic without
 * enclave hardware support getting in the way.
 *
 * The remote attestation of the enclave will be fake and insecure: the code measurement, whilst unqiue to each enclave
 * class, is not a true representation.
 *
 * @param T The type of the enclave class that this mock is connected to.
 *
 * @property enclave Gives direct access to the instantiated [Enclave] subclass, useful for making assertions about the
 * internal state of the object (if any).
 */
class MockHost<T : Enclave> private constructor(val enclave: T) : EnclaveHost() {
    companion object {
        internal fun <T : Enclave> create(enclave: T): MockHost<T> = MockHost(enclave)

        /**
         * Creates a [MockHost] suitable for unit tests, that connects to the given [Enclave].
         */
        @JvmStatic
        fun <T : Enclave> loadMock(enclaveClass: Class<T>): MockHost<T> {
            return MockInternals.createMock(enclaveClass, isvProdId = 1, isvSvn = 1)
        }

        /** A simple Kotlin helper for [loadMock]. */
        inline fun <reified T : Enclave> loadMock(): MockHost<T> = loadMock(T::class.java)
    }
}
