package com.r3.conclave.host.internal

import com.r3.conclave.common.EnclaveMode
import com.r3.conclave.common.internal.CallHandler
import com.r3.conclave.common.internal.Cursor
import com.r3.conclave.common.internal.HostCallType
import com.r3.conclave.common.internal.SgxReport
import com.r3.conclave.host.AttestationParameters
import com.r3.conclave.host.internal.attestation.*
import java.io.IOException
import java.net.URL
import java.nio.ByteBuffer
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption.REPLACE_EXISTING
import kotlin.io.path.deleteIfExists

class NativeEnclaveHandle(
    override val enclaveMode: EnclaveMode,
    override val enclaveClassName: String,
    enclaveFileUrl: URL,
) : EnclaveHandle {
    private val enclaveFile: Path
    private val enclaveId: Long
    override val enclaveInterface: NativeHostEnclaveInterface
    override lateinit var quotingService: EnclaveQuoteService

    /**
     * Handler for servicing requests from the enclave for signed quotes.
     */
    private inner class GetSignedQuoteHandler : CallHandler {
        override fun handleCall(parameterBuffer: ByteBuffer): ByteBuffer {
            val report = Cursor.slice(SgxReport, parameterBuffer)
            val signedQuote = quotingService.retrieveQuote(report)
            return signedQuote.buffer
        }
    }

    init {
        require(enclaveMode != EnclaveMode.MOCK)
        NativeLoader.loadHostLibraries(enclaveMode)
        enclaveFile = Files.createTempFile(enclaveClassName, "signed.so").toAbsolutePath()
        enclaveFileUrl.openStream().use { Files.copy(it, enclaveFile, REPLACE_EXISTING) }
        enclaveId = Native.createEnclave(enclaveFile.toString(), enclaveMode != EnclaveMode.RELEASE)
        enclaveInterface = NativeHostEnclaveInterface(enclaveId)
        NativeApi.registerHostEnclaveInterface(enclaveId, enclaveInterface)

        enclaveInterface.apply {
            registerCallHandler(HostCallType.GET_SIGNED_QUOTE, GetSignedQuoteHandler())
        }
    }

    override fun initialise(attestationParameters: AttestationParameters?) {
        synchronized(this) {
            quotingService = when(attestationParameters) {
                is AttestationParameters.EPID -> EnclaveQuoteServiceEPID(attestationParameters)
                is AttestationParameters.DCAP -> EnclaveQuoteServiceDCAP
                null -> EnclaveQuoteServiceMock
            }
            initializeEnclave(enclaveClassName)
        }
    }

    override fun destroy() {
        Native.destroyEnclave(enclaveId)
        try {
            enclaveFile.deleteIfExists()
        } catch (e: IOException) {
            logger.debug("Unable to delete temp file $enclaveFile", e)
        }
    }

    override val mockEnclave: Any get() {
        throw IllegalStateException("The enclave instance can only be accessed in mock mode.")
    }

    private companion object {
        private val logger = loggerFor<NativeEnclaveHandle>()
    }
}
