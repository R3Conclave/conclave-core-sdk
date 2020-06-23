package com.r3.conclave.enclave.internal

import com.r3.conclave.common.EnclaveMode
import com.r3.conclave.common.internal.*
import com.r3.conclave.common.internal.handler.HandlerConnected
import com.r3.conclave.common.internal.handler.Sender
import com.r3.conclave.enclave.Enclave
import java.nio.ByteBuffer

@PotentialPackagePrivate
object NativeEnclaveEnvironment : EnclaveEnvironment {
    private var isEnclaveDebug: Boolean? = null

    // The use of reflection is not ideal but Kotlin does not have the concept of package-private visibility.
    // Kotlin's internal visibility is still public under the hood and can be accessed without suppressing access checks.
    private val initialiseMethod = Enclave::class.java.getDeclaredMethod("initialise", EnclaveEnvironment::class.java, Sender::class.java).apply { isAccessible = true }

    /** The singleton instance of the user supplied enclave. */
    private var singletonHandler: HandlerConnected<*>? = null

    /**
     * The ECALL entry point. This code does *not* handle exceptions and must be done e.g. by using [com.r3.conclave.core.enclave.RootEnclave]
     *
     * @param input The chunk of data sent from the host.
     */
    @JvmStatic
    @Suppress("UNUSED")
    fun enclaveEntry(input: ByteArray) {
        val singletonHandler = synchronized(this) {
            singletonHandler ?: run {
                // The first ECALL is always the enclave class name, which we only use to instantiate the enclave.
                singletonHandler = initialiseEnclave(input)
                null
            }
        }
        singletonHandler?.onReceive(ByteBuffer.wrap(input).asReadOnlyBuffer())
    }

    private fun initialiseEnclave(input: ByteArray): HandlerConnected<*> {
        val enclaveClassName = String(input)
        // TODO We need to load the enclave in a custom classloader that locks out internal packages of the public API.
        //      This wouldn't be needed with Java modules, but the enclave environment runs in Java 8.
        val enclaveClass = Class.forName(enclaveClassName)
        val enclave = enclaveClass.asSubclass(Enclave::class.java).getConstructor().newInstance()
        return initialiseMethod.invoke(enclave, this, NativeOcallSender) as HandlerConnected<*>
    }

    override fun createReport(targetInfoIn: ByteArray?, reportDataIn: ByteArray?, reportOut: ByteArray) {
        Native.createReport(targetInfoIn, reportDataIn, reportOut)
    }

    override fun randomBytes(output: ByteArray, offset: Int, length: Int) {
        Native.randomBytes(output, offset, length)
    }

    override val enclaveMode: EnclaveMode
        get() {
            return when {
                // Important that the simulation flag is checked first because simulation mode has debug=true as well.
                Native.isEnclaveSimulation() -> EnclaveMode.SIMULATION
                isDebugMode() -> EnclaveMode.DEBUG
                else -> EnclaveMode.RELEASE
            }
        }

    /**
     * @return true if the enclave was loaded in debug mode, i.e. its report's `DEBUG` flag is set, false otherwise.
     */
    private fun isDebugMode(): Boolean {
        val isEnclaveDebug = this.isEnclaveDebug
        return if (isEnclaveDebug == null) {
            val report = Cursor.allocate(SgxReport)
            createReport(null, null, report.getBuffer().array())
            val enclaveFlags = report[SgxReport.body][SgxReportBody.attributes][SgxAttributes.flags].read()
            val result = enclaveFlags and SgxEnclaveFlags.DEBUG != 0L
            this.isEnclaveDebug = result
            result
        } else {
            isEnclaveDebug
        }
    }

    // Static enclave registration
    init {
        EnclaveSecurityProvider.register()
    }
}
