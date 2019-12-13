package com.r3.sgx.multiplex.client

import com.r3.sgx.multiplex.common.sha256
import java.nio.ByteBuffer
import java.util.concurrent.CompletableFuture

interface LoadingConnection {
    /**
     * Upload a dynamic enclave and request a connection for it.
     * @param jarData The byte contents of an enclave's "fat jar".
     * @param jarHash The SHA-256 hash of the contents of [jarData].
     * @return A [CompletableFuture] for the connection to the dynamic enclave.
     */
    fun sendJar(jarData: ByteBuffer, jarHash: ByteBuffer): CompletableFuture<EnclaveConnection>

    /**
     * Upload a dynamic enclave and request a connection for it.
     * @param jarData The byte contents of an enclave's "fat jar".
     * @return A [CompletableFuture] for the connection to the dynamic enclave.
     */
    @JvmDefault
    fun sendJar(jarData: ByteBuffer): CompletableFuture<EnclaveConnection> = sendJar(jarData, ByteBuffer.wrap(jarData.sha256))

    /**
     * Request a connection for an existing dynamic enclave.
     * @param jarHash The SHA-256 of an existing dynamic enclave.
     * @return A [CompletableFuture] for the connection to the dynamic enclave.
     */
    fun useJar(jarHash: ByteBuffer): CompletableFuture<EnclaveConnection>
}
