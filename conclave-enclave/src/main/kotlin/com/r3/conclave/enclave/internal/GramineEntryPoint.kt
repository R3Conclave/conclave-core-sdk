package com.r3.conclave.enclave.internal

import com.r3.conclave.common.internal.CallHandler
import com.r3.conclave.common.internal.EnclaveCallType
import com.r3.conclave.utilities.internal.getRemainingString
import java.lang.NumberFormatException
import java.nio.ByteBuffer
import kotlin.system.exitProcess

class GramineEntryPoint {

    companion object {
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

        @JvmStatic
        fun main(args: Array<String>) {
            val port = getPortFromArgs(args)
            val hostInterface = SocketEnclaveHostInterface("127.0.0.1", port, MAX_CONCURRENCY).apply { start() }

            /** Register the enclave initialisation call handler. */
            hostInterface.registerCallHandler(EnclaveCallType.INITIALISE_ENCLAVE, object : CallHandler {
                override fun handleCall(parameterBuffer: ByteBuffer): ByteBuffer? {
                    throw Exception("Cannot initialise enclave '${parameterBuffer.getRemainingString()}', operation not implemented!")
                }
            })

            hostInterface.use {
                /** TODO: Actual enclave stuff. */
                Thread.sleep(10000)
            }
        }
    }
}
