package com.r3.sgx.core.enclave

import com.r3.sgx.core.common.*
import java.nio.ByteBuffer

/**
 * An [Enclavelet] is an enclave adhering to a specific binary protocol that allows it to be loaded onto a generic
 * enclave host infrastructure. In particular it supports channels and EPID based attestation.
 *
 * Channel support means that we can think of an enclavelet as a kind of server, where enclave clients can connect by
 * creating channels and exchanging binary blobs. [createHandler] is the factory method for handlers of such channels.
 *
 * EPID attestation support means that when the enclave is initially loaded the enclave will generate a small piece of
 * data using [createReportData] to be later included in an EPID-based attestation quote. The host will then request
 * reports embedding this data, to be later sent to the Intel Attestation Service for validation. Note that this data is
 * generated once and is never invalidated.
 */
abstract class Enclavelet : RootEnclave() {
    /**
     * Create the report data to be included in the fully attested quote. A typical implementation will generate a
     * cryptographic key pair and return the hash of the public key. Later on the keypair can be used for example to
     * authenticate the enclave during DH, provide signatures over computed data, or perhaps to sign further keys.
     * @param api the enclave API.
     */
    abstract fun createReportData(api: EnclaveApi): Cursor<ByteBuffer, SgxReportData>
    /**
     * Create a [Handler] for a requested channel. The channels themselves are created by the host (possibly triggered
     * remotely) using [ChannelInitiatingHandler.Connection.addDownstream], which creates the channel and adds the host
     * side handler.
     * @param api the enclave API.
     */
    abstract fun createHandler(api: EnclaveApi): Handler<*>

    final override fun initialize(api: EnclaveApi, mux: SimpleMuxingHandler.Connection) {
        val reportData = createReportData(api)
        mux.addDownstream(EnclaveletChannelHandlingHandler(this, api))
        mux.addDownstream(EnclaveletEpidAttestationEnclaveHandler(api, reportData))
    }

    class EnclaveletChannelHandlingHandler(
            val enclavelet: Enclavelet,
            val api: EnclaveApi
    ) : ChannelHandlingHandler() {
        override fun createHandler() = enclavelet.createHandler(api)
    }

    class EnclaveletEpidAttestationEnclaveHandler(
            api: EnclaveApi,
            override val reportData: Cursor<ByteBuffer, SgxReportData>
    ) : EpidAttestationEnclaveHandler(api)
}
