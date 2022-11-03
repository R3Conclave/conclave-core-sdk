package com.r3.conclave.enclave.internal

import com.r3.conclave.common.EnclaveMode
import com.r3.conclave.common.internal.CallHandler
import com.r3.conclave.common.internal.EnclaveCallType
import com.r3.conclave.enclave.Enclave
import com.r3.conclave.utilities.internal.getRemainingString
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method
import java.nio.ByteBuffer
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.system.exitProcess

object GramineEntryPoint {
    private const val USAGE_STRING = "usage: GramineEntryPoint <port>"
    private const val EXIT_ERR = -1

    /** The host passes the port on the command line. */
    private fun getPortFromArgs(args: Array<String>): Int {
        if (args.isEmpty()) {
            System.err.println(USAGE_STRING)
            exitProcess(EXIT_ERR)
        }

        val port = try {
            args[0].toInt()
        } catch (e: NumberFormatException) {
            System.err.println("${args[0]} is not a valid port number.")
            exitProcess(EXIT_ERR)
        }

        if (port > 65535 || port < 0) {
            System.err.println("$port is not a valid port number. Value must be between 0 and 65535.")
            exitProcess(EXIT_ERR)
        }

        return port
    }

    private fun initialiseEnclave(
        enclaveClassName: String,
        hostInterface: SocketEnclaveHostInterface,
        pythonScript: Path?
    ) {
        val enclaveClass = Class.forName(enclaveClassName)
        val enclave = enclaveClass.asSubclass(Enclave::class.java)
                .getDeclaredConstructor()
                .apply { isAccessible = true }
                .newInstance()
        if (pythonScript != null) {
            require(enclave.javaClass.name == "com.r3.conclave.python.PythonEnclaveAdapter")
            enclave.javaClass
                .getAccessibleMethod("setUserPythonScript", Path::class.java)
                .execute(enclave, pythonScript)
        }
        val env = GramineDirectEnclaveEnvironment(enclaveClass, hostInterface)
        env.hostInterface.sanitiseExceptions = (env.enclaveMode == EnclaveMode.RELEASE)
        Enclave::class.java
            .getAccessibleMethod("initialise", EnclaveEnvironment::class.java)
            .execute(enclave, env)
    }

    private fun Class<*>.getAccessibleMethod(name: String, vararg parameterTypes: Class<*>): Method {
        return getDeclaredMethod(name, *parameterTypes).apply { isAccessible = true }
    }

    private fun Method.execute(receiver: Any, vararg parameters: Any) {
        try {
            invoke(receiver, *parameters)
        } catch (e: InvocationTargetException) {
            throw e.cause ?: e
        }
    }

    @JvmStatic
    fun main(args: Array<String>) {
        val port = getPortFromArgs(args)
        val hostInterface = SocketEnclaveHostInterface("127.0.0.1", port)
        // TODO The enclave should expect the python script to exist at a pre-defined location in the Gramine
        //  filesystem.
        val pythonScript = args.getOrNull(1)?.let { Paths.get(it) }

        /** Register the enclave initialisation call handler. */
        hostInterface.registerCallHandler(EnclaveCallType.INITIALISE_ENCLAVE, object : CallHandler {
            override fun handleCall(parameterBuffer: ByteBuffer): ByteBuffer? {
                initialiseEnclave(parameterBuffer.getRemainingString(), hostInterface, pythonScript)
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
