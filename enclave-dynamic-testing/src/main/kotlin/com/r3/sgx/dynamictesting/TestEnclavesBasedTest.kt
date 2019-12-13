package com.r3.sgx.dynamictesting

import com.r3.sgx.core.common.Handler
import com.r3.sgx.core.enclave.Enclave
import com.r3.sgx.core.host.EnclaveHandle
import com.r3.sgx.core.host.EnclaveLoadMode
import com.r3.sgx.core.host.NativeHostApi
import com.r3.sgx.testing.MockEcallSender
import com.r3.sgx.testing.RootHandler
import org.junit.ClassRule
import org.junit.runners.Parameterized.Parameters
import java.io.File
import java.util.function.Consumer

enum class EnclaveTestMode {
    Native,
    Mock
}

/**
 * A base class for test suites that manipulate enclaves.
 * @param mode What mode the test should be run in.
 *   If [mode] = [EnclaveTestMode.Native] a binary enclave will be linked and used.
 *   If [mode] = [EnclaveTestMode.Mock] the enclave is instantiated in-memory, allowing IDE debugging. This is only
 *     useful for development.
 */
open class TestEnclavesBasedTest(
        val mode: EnclaveTestMode = EnclaveTestMode.Native
) {
    companion object {
        @JvmField
        @ClassRule
        val testEnclaves = TestEnclaves()

        @Parameters
        @JvmStatic
        fun modes() = EnclaveTestMode.values().map { elt -> Array(1) { elt } }.toList()
    }

    @JvmOverloads
    fun createEnclave(enclaveClass: Class<out Enclave>, enclaveBuilder: EnclaveBuilder = EnclaveBuilder()): RootHandler.Connection {
        return createEnclaveWithHandler(RootHandler(), enclaveClass, enclaveBuilder).connection
    }

    @JvmOverloads
    fun <CONNECTION> createEnclaveWithHandler(
            handler: Handler<CONNECTION>,
            enclaveClass: Class<out Enclave>,
            enclaveBuilder: EnclaveBuilder = EnclaveBuilder()
    ): EnclaveHandle<CONNECTION> {
        return when (mode) {
            EnclaveTestMode.Native -> {
                val enclaveFile = testEnclaves.getEnclave(enclaveClass, enclaveBuilder)
                createEnclaveWithHandler(handler, enclaveFile)
            }
            EnclaveTestMode.Mock -> createMockEnclaveWithHandler(handler, enclaveClass)
        }
    }

    fun <CONNECTION> createEnclaveWithHandler(
            handler: Handler<CONNECTION>,
            enclaveClass: Class<out Enclave>,
            enclaveFile: File
    ): EnclaveHandle<CONNECTION> {
        return when (mode) {
            EnclaveTestMode.Native -> createEnclaveWithHandler(handler, enclaveFile)
            EnclaveTestMode.Mock -> createMockEnclaveWithHandler(handler, enclaveClass)
        }
    }

    fun <CONNECTION> createEnclaveWithHandler(
            handler: Handler<CONNECTION>,
            enclaveFile: File
    ): EnclaveHandle<CONNECTION> {
        val hostApi = NativeHostApi(EnclaveLoadMode.SIMULATION)
        return hostApi.createEnclave(handler, enclaveFile)
    }

    fun <CONNECTION> createMockEnclaveWithHandler(
            handler: Handler<CONNECTION>,
            enclaveClass: Class<out Enclave>
    ): EnclaveHandle<CONNECTION> {
        val enclave = enclaveClass.newInstance()
        return MockEcallSender(handler, enclave)
    }

    @JvmOverloads
    fun <CONNECTION> withEnclaveHandle(rootHandler: Handler<CONNECTION>,
                                       enclaveClass: Class<out Enclave>,
                                       builder: EnclaveBuilder = EnclaveBuilder(),
                                       block: Consumer<EnclaveHandle<CONNECTION>>) {
        val enclaveHandle = createEnclaveWithHandler(rootHandler, enclaveClass, builder)
        try {
            block.accept(enclaveHandle)
        } finally {
            if (mode == EnclaveTestMode.Native) {
                @Suppress("DEPRECATION")
                enclaveHandle.destroy()
            }
        }
    }

}

