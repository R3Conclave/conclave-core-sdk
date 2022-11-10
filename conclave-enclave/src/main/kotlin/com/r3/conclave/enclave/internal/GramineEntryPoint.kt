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
import java.util.*
import kotlin.system.exitProcess

object GramineEntryPoint {
    private const val USAGE_STRING = "usage: GramineEntryPoint <port>"
    private const val ENCLAVE_METADATA_FILE = "enclave-metadata.properties"
    private const val EXIT_ERR = -1

    /** Enclave metadata variables */
    private var isSimulation: Boolean? = null
    private var signingKeyMeasurement: ByteArray? = null
    private var pythonEnclaveScript: Path? = null

    @JvmStatic
    fun main(args: Array<String>) {
        val port = getPortFromArgs(args)
        val hostInterface = SocketEnclaveHostInterface("127.0.0.1", port)

        /** Load the enclave metadata properties file */
        loadEnclaveMetadata()

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

    /**
     * Load enclave metadata properties file.
     * This file contains properties required to load the enclave, such as the signing key measurement, threading level,
     * and whether to use gramine-sgx or gramine-direct.
     * For more information, see [com.r3.conclave.plugin.enclave.gradle.gramine.GenerateGramineEnclaveMetadata].
     */
    private fun loadEnclaveMetadata() {
        val properties = Paths.get(ENCLAVE_METADATA_FILE).toFile().reader().use { reader ->
            Properties().apply { load(reader) }
        }

        val isSimulationString = checkNotNull(properties["isSimulation"]).toString()
        check(isSimulationString == "true" || isSimulationString == "false")
        isSimulation = isSimulationString == "true"

        /** The signing key measurement is only part of the metadata in simulation mode. */
        val signingKeyMeasurementStr = properties["signingKeyMeasurement"]?.toString()
        if (isSimulation!!) {
            signingKeyMeasurement = Base64.getDecoder().decode(signingKeyMeasurementStr)
            check(signingKeyMeasurement!!.size == 32)
        } else {
            /**
             * The signing key measurement must *not* be present in non simulation modes!
             * Run a quick sanity check here to confirm.
             */
            check(signingKeyMeasurementStr == null)
        }

        /** Set the python enclave path script if present in the metadata file. */
        pythonEnclaveScript = properties["pythonEnclaveScript"]?.let {
            Paths.get(it.toString())
        }
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
        if (pythonEnclaveScript != null) {
            require(enclave.javaClass.name == "com.r3.conclave.python.PythonEnclaveAdapter")
            enclave.javaClass
                    .getAccessibleMethod("setUserPythonScript", Path::class.java)
                    .execute(enclave, pythonEnclaveScript!!)
        }
        val env = createEnclaveEnvironment(enclaveClass, hostInterface)
        hostInterface.sanitiseExceptions = (env.enclaveMode == EnclaveMode.RELEASE)
        Enclave::class.java
            .getAccessibleMethod("initialise", EnclaveEnvironment::class.java)
            .execute(enclave, env)
    }

    private fun createEnclaveEnvironment(enclaveClass: Class<*>, hostInterface: SocketEnclaveHostInterface): EnclaveEnvironment {
        return if (isSimulation!!) {
            GramineDirectEnclaveEnvironment(enclaveClass, hostInterface, signingKeyMeasurement!!)
        } else {
            System.err.println("Gramine SGX is not yet implemented.")
            exitProcess(EXIT_ERR)
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
