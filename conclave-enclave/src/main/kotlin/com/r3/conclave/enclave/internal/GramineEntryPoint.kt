package com.r3.conclave.enclave.internal

import com.r3.conclave.common.EnclaveMode
import com.r3.conclave.common.internal.CallHandler
import com.r3.conclave.common.internal.EnclaveCallType
import com.r3.conclave.enclave.Enclave
import com.r3.conclave.utilities.internal.getRemainingString
import java.lang.NumberFormatException
import java.nio.ByteBuffer
import kotlin.system.exitProcess

object GramineEntryPoint {
    private const val USAGE_STRING = "usage: GramineEntryPoint <port>"
    private const val EXIT_ERR = -1

    // TODO: Determine this properly!
    private const val MAX_CONCURRENCY = 8

    /** The host passes the port on the command line. */
    private fun getPortFromArgs(args: Array<String>): Int {
        if (args.isEmpty()) {
            System.err.println(USAGE_STRING)
            exitProcess(EXIT_ERR)
        }

        return try {
            args[0].toUInt().toInt()
        } catch (e: NumberFormatException) {
            System.err.println("Expected port number, but got '${args[0]}'.")
            exitProcess(EXIT_ERR)
        }
    }

    private fun initialiseEnclave(enclaveClassName: String, hostInterface: SocketEnclaveHostInterface) {
        val enclaveClass = Class.forName(enclaveClassName)
        val env = GramineEnclaveEnvironment(enclaveClass, hostInterface)
        val enclave = enclaveClass.asSubclass(Enclave::class.java)
                .getDeclaredConstructor()
                .apply { isAccessible = true }
                .newInstance()
        val initialiseMethod = Enclave::class.java.getDeclaredMethod(
                "initialise", EnclaveEnvironment::class.java).apply { isAccessible = true }
        env.hostInterface.sanitiseExceptions = (env.enclaveMode == EnclaveMode.RELEASE)
        initialiseMethod.invoke(enclave, env)
    }

    @JvmStatic
    fun main(args: Array<String>) {
        val port = getPortFromArgs(args)
        val hostInterface = SocketEnclaveHostInterface("127.0.0.1", port, MAX_CONCURRENCY)

        /** Register the enclave initialisation call handler. */
        hostInterface.registerCallHandler(EnclaveCallType.INITIALISE_ENCLAVE, object : CallHandler {
            override fun handleCall(parameterBuffer: ByteBuffer): ByteBuffer? {
                initialiseEnclave(parameterBuffer.getRemainingString(), hostInterface)
                return null
            }
        })

        /**
         * Start the interface and await termination.
         * TODO: Re-use the main thread (here) for the interface receive loop
         */
        hostInterface.use {
            it.start()
            it.awaitTermination()
        }
    }
}
