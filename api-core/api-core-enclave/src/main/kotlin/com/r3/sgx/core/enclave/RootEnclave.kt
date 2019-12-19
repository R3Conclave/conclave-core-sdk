package com.r3.sgx.core.enclave

import com.r3.sgx.core.common.*

/**
 * A base class for enclaves with initialization code.
 */
abstract class RootEnclave : Enclave {
    /**
     * Initializes the enclave. It's only called once.
     * @param api the [EnclaveApi] object to access enclave functionality
     * @param mux the mux [Handler]
     */
    abstract fun initialize(api: EnclaveApi, mux: SimpleMuxingHandler.Connection)

    final override fun initialize(api: EnclaveApi, upstream: Sender): HandlerConnected<*>? {
        val exposeErrors = api.isSimulation() || api.isDebugMode()
        val rootHandler = ExceptionSendingHandler(exposeErrors = exposeErrors)
        val root = rootHandler.connect(upstream)
        val mux = root.setDownstream(SimpleMuxingHandler())
        try {
            initialize(api, mux)
        } catch (exception: Throwable) {
            root.sendException(exception)
            return null
        }
        return HandlerConnected(rootHandler, root)
    }
}
