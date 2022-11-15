package com.r3.conclave.enclave.internal

import com.r3.conclave.common.EnclaveMode
import com.r3.conclave.common.internal.CallHandler
import com.r3.conclave.common.internal.EnclaveCallType
import com.r3.conclave.common.internal.PluginUtils.GRAMINE_MANIFEST
import com.r3.conclave.common.internal.PluginUtils.GRAMINE_SGX_MANIFEST
import com.r3.conclave.common.internal.PluginUtils.PYTHON_FILE
import com.r3.conclave.enclave.Enclave
import com.r3.conclave.utilities.internal.getRemainingString
import com.r3.conclave.utilities.internal.parseHex
import java.io.File
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method
import java.nio.ByteBuffer
import java.nio.file.Path
import kotlin.system.exitProcess
import java.lang.Boolean.parseBoolean
import java.nio.file.Paths

object GramineEntryPoint {
    private const val USAGE_STRING = "usage: GramineEntryPoint <port>"
    private const val EXIT_ERR = -1

    /** Enclave metadata, retrieved from the manifest. */
    private val enclaveMode = EnclaveMode.valueOf(System.getenv("CONCLAVE_ENCLAVE_MODE")!!.uppercase())
    private val isPythonEnclave = parseBoolean(System.getenv("CONCLAVE_IS_PYTHON_ENCLAVE")!!)
    private val isSgxDebug = parseBoolean(System.getenv("SGX_DEBUG"))
    private val simulationMrSigner = System.getenv("CONCLAVE_SIMULATION_MRSIGNER")?.let { parseHex(it) }
    private val conclaveWorkerThreads = System.getenv("CONCLAVE_ENCLAVE_WORKER_THREADS")!!.toInt()
    private val commonErrorMessage = "Manifest not present when initialising the enclave in $enclaveMode mode"

    @JvmStatic
    fun main(args: Array<String>) {
        val port = getPortFromArgs(args)
        val hostInterface = SocketEnclaveHostInterface("127.0.0.1", port, conclaveWorkerThreads)

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
        hostInterface: SocketEnclaveHostInterface
    ) {
        val enclaveClass = Class.forName(enclaveClassName)
        val enclave = enclaveClass.asSubclass(Enclave::class.java)
                .getDeclaredConstructor()
                .apply { isAccessible = true }
                .newInstance()
        if (isPythonEnclave) {
            require(enclave.javaClass.name == "com.r3.conclave.python.PythonEnclaveAdapter")
            enclave.javaClass
                    .getAccessibleMethod("setUserPythonScript", Path::class.java)
                    .execute(enclave, Paths.get(PYTHON_FILE))
        }
        validateEnclaveMode()
        val env = GramineEnclaveEnvironment(enclaveClass, hostInterface, simulationMrSigner?: byteArrayOf(), enclaveMode)
        hostInterface.sanitiseExceptions = (env.enclaveMode == EnclaveMode.RELEASE)
        Enclave::class.java
            .getAccessibleMethod("initialise", EnclaveEnvironment::class.java)
            .execute(enclave, env)
    }

    private fun validateEnclaveMode() {
        when (enclaveMode) {
            EnclaveMode.SIMULATION -> {
                require(File(GRAMINE_MANIFEST).exists()) { "Gramine Direct $commonErrorMessage" }
            }

            EnclaveMode.DEBUG -> {
                require(File(GRAMINE_SGX_MANIFEST).exists()) { "Gramine SGX $commonErrorMessage" }
                require(isSgxDebug) { "Gramine SGX debug flag not enabled in $enclaveMode mode" }
            }

            EnclaveMode.RELEASE -> {
                require(File(GRAMINE_SGX_MANIFEST).exists()) { "Gramine SGX $commonErrorMessage" }
                require(!isSgxDebug) { "Gramine SGX debug flag not disabled in $enclaveMode mode" }
            }

            EnclaveMode.MOCK -> {
                throw IllegalArgumentException("Developer error: MOCK mode not supported in Gramine")
            }
        }
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
}
