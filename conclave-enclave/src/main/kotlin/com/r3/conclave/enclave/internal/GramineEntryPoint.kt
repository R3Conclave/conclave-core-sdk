package com.r3.conclave.enclave.internal

import com.r3.conclave.common.EnclaveMode
import com.r3.conclave.common.internal.CallHandler
import com.r3.conclave.common.internal.EnclaveCallType
import com.r3.conclave.enclave.Enclave
import com.r3.conclave.utilities.internal.getRemainingString
import java.lang.NumberFormatException
import java.nio.ByteBuffer
import java.nio.file.Paths
import java.util.*
import kotlin.properties.Delegates
import kotlin.system.exitProcess

object GramineEntryPoint {
    private const val USAGE_STRING = "usage: GramineEntryPoint <port>"
    private const val ENCLAVE_METADATA_FILE = "enclave-metadata.properties"
    private const val EXIT_ERR = -1

    /** Enclave metadata variables */
    private var isSimulation by Delegates.notNull<Boolean>()
    private lateinit var signingKeyMeasurement: ByteArray

    /** Load enclave metadata from the enclave properties file. */
    private fun loadEnclaveMetadata() {
        val properties = Paths.get(ENCLAVE_METADATA_FILE).toFile().reader().use { reader ->
            Properties().apply { load(reader) }
        }

        val isSimulationString = checkNotNull(properties["isSimulation"]).toString()
        check(isSimulationString == "true" || isSimulationString == "false")
        isSimulation = isSimulationString == "true"

        /** The signing key measurement is only part of the metadata in simulation mode. */
        val signingKeyMeasurementStr = properties["signingKeyMeasurement"]?.toString()
        if (isSimulation) {
            signingKeyMeasurement = Base64.getDecoder().decode(signingKeyMeasurementStr)
            check(signingKeyMeasurement.size == 32)
        } else {
            /**
             * The signing key measurement must *not* be present in non simulation modes!
             * Run a quick sanity check here to confirm.
             */
            check(signingKeyMeasurementStr == null)
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

    private fun createEnclaveEnvironment(enclaveClass: Class<*>, hostInterface: SocketEnclaveHostInterface): EnclaveEnvironment {
        return if (isSimulation) {
            GramineDirectEnclaveEnvironment(enclaveClass, hostInterface, signingKeyMeasurement)
        } else {
            System.err.println("Gramine SGX is not yet implemented.")
            exitProcess(EXIT_ERR)
        }
    }

    private fun initialiseEnclave(enclaveClassName: String, hostInterface: SocketEnclaveHostInterface) {
        val enclaveClass = Class.forName(enclaveClassName)
        val env = createEnclaveEnvironment(enclaveClass, hostInterface)
        val enclave = enclaveClass.asSubclass(Enclave::class.java)
                .getDeclaredConstructor()
                .apply { isAccessible = true }
                .newInstance()
        val initialiseMethod = Enclave::class.java.getDeclaredMethod(
                "initialise", EnclaveEnvironment::class.java).apply { isAccessible = true }
        hostInterface.sanitiseExceptions = (env.enclaveMode == EnclaveMode.RELEASE)
        initialiseMethod.invoke(enclave, env)
    }

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
}
