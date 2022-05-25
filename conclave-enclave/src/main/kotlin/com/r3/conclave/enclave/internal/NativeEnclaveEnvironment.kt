package com.r3.conclave.enclave.internal

import com.r3.conclave.common.EnclaveMode
import com.r3.conclave.common.internal.*
import com.r3.conclave.common.internal.SgxAttributes.flags
import com.r3.conclave.common.internal.SgxReport.body
import com.r3.conclave.common.internal.SgxReportBody.attributes
import com.r3.conclave.common.internal.handler.HandlerConnected
import com.r3.conclave.common.internal.handler.Sender
import com.r3.conclave.enclave.Enclave
import com.r3.conclave.utilities.internal.EnclaveContext
import com.r3.conclave.utilities.internal.getRemainingBytes
import com.r3.conclave.utilities.internal.getRemainingString
import java.lang.reflect.InvocationTargetException
import java.nio.ByteBuffer
import java.security.SecureRandom
import java.util.*
import java.util.concurrent.atomic.AtomicLong

@PotentialPackagePrivate
class NativeEnclaveEnvironment(
    enclaveClass: Class<*>
) : EnclaveEnvironment(loadEnclaveProperties(enclaveClass, false), null) {
    companion object {
        // The use of reflection is not ideal but Kotlin does not have the concept of package-private visibility.
        // Kotlin's internal visibility is still public under the hood and can be accessed without suppressing access checks.
        private val initialiseMethod =
                Enclave::class.java.getDeclaredMethod("initialise", EnclaveEnvironment::class.java, Sender::class.java)
                        .apply { isAccessible = true }

        /** The singleton instance of the user supplied enclave. */
        private var singletonHandler: HandlerConnected<*>? = null

        /**
         * The ECALL entry point.
         *
         * @param buffer The chunk of data sent from the host.
         */
        @JvmStatic
        fun enclaveEntry(buffer: ByteBuffer) {
            val singletonHandler = synchronized(this) {
                singletonHandler ?: run {
                    // The first ECALL is always the enclave class name, which we only use to instantiate the enclave.
                    singletonHandler = initialiseEnclave(buffer)
                    null
                }
            }
            singletonHandler?.onReceive(buffer)
        }

        private fun seedRandom() {
            // java.util.Random is not designed for secure use and is even more insecure inside an
            // enclave as it uses nanoTime for seeding. We can harden this implementation by seeding
            // java.util.Random with java.util.SecureRandom in order to create a pseudorandom sequence
            // from a truly random seed.

            // There is no way to provide a global seed for the random number. Looking at util/Random.java
            // in the OpenJDK though you can see that it is seeded from the time, combined with a field
            // named "seedUniquifier" that is updated on each random object creation. We can get hold
            // of this field and initialise it with a true random number to create a truly random
            // seed.
            try {
                // Get the field that is used to ensure each instance of Random() creates a new
                // sequence of numbers, even if the time (used as a seed) has not changed.
                val seedUniquifierField = Random::class.java.getDeclaredField("seedUniquifier")
                seedUniquifierField.isAccessible = true
                val seedUniquifier = seedUniquifierField.get(null) as AtomicLong
                seedUniquifierField.isAccessible = false

                // Set the field to a truly random initialiser value. This is XOR'd with the system
                // time (which comes from the host so may not be safe) to seed the random number
                // generator.
                val seed = SecureRandom()
                seedUniquifier.set(seed.nextLong())
            } catch (e: Exception) {
                throw InternalError("Could not set Random seed. Failed to access the seedUniquifier field.", e)
            }
        }

        private fun initialiseEnclave(buffer: ByteBuffer): HandlerConnected<*> {
            seedRandom()

            val enclaveClassName = buffer.getRemainingString()
            // TODO We need to load the enclave in a custom classloader that locks out internal packages of the public API.
            //      This wouldn't be needed with Java modules, but the enclave environment runs in Java 8.
            val enclaveClass = Class.forName(enclaveClassName)
            return try {
                val enclave = enclaveClass.asSubclass(Enclave::class.java)
                    .getDeclaredConstructor()
                    .apply { isAccessible = true }
                    .newInstance()
                val env = NativeEnclaveEnvironment(enclaveClass)
                initialiseMethod.invoke(enclave, env, NativeOcallSender) as HandlerConnected<*>
            } catch (e: InvocationTargetException) {
                throw e.cause ?: e
            }
        }
    }

    private var isEnclaveDebug: Boolean? = null

    override fun createReport(
        targetInfo: ByteCursor<SgxTargetInfo>?,
        reportData: ByteCursor<SgxReportData>?
    ): ByteCursor<SgxReport> {
        val report = Cursor.allocate(SgxReport)
        Native.createReport(
            targetInfo?.buffer?.getRemainingBytes(avoidCopying = true),
            reportData?.buffer?.getRemainingBytes(avoidCopying = true),
            report.buffer.array()
        )
        return report
    }

    override fun setupFileSystems(
        inMemoryFsSize: Long,
        persistentFsSize: Long,
        inMemoryMountPath: String,
        persistentMountPath: String,
        encryptionKey: ByteArray
    ) {
        val inMemoryMountPathModified =
            if (inMemoryMountPath.endsWith("/")) inMemoryMountPath else "$inMemoryMountPath/"
        val persistentMountPathModified =
            if (persistentMountPath.endsWith("/")) persistentMountPath else "$persistentMountPath/"
        Native.setupFileSystems(
            inMemoryFsSize,
            persistentFsSize,
            inMemoryMountPathModified,
            persistentMountPathModified,
            encryptionKey
        )
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

    override fun sealData(toBeSealed: PlaintextAndEnvelope): ByteArray {
        val sealedData = ByteArray(Native.calcSealedBlobSize(
            toBeSealed.plaintext.size,
            toBeSealed.authenticatedData?.size ?: 0
        ))
        Native.sealData(
            sealedData,
            0,
            sealedData.size,
            toBeSealed.plaintext,
            0,
            toBeSealed.plaintext.size,
            toBeSealed.authenticatedData,
            0,
            toBeSealed.authenticatedData?.size ?: 0
        )
        return sealedData
    }

    override fun unsealData(sealedBlob: ByteArray): PlaintextAndEnvelope {
        require(sealedBlob.isNotEmpty())
        val plaintext = ByteArray(Native.plaintextSizeFromSealedData(sealedBlob))
        val authenticatedData = Native.authenticatedDataSize(sealedBlob).let { if (it > 0) ByteArray(it) else null }

        Native.unsealData(
            sealedBlob,
            0,
            sealedBlob.size,
            plaintext,
            0,
            plaintext.size,
            authenticatedData,
            0,
            authenticatedData?.size ?: 0
        )

        return PlaintextAndEnvelope(plaintext, authenticatedData)
    }

    override fun getSecretKey(keyRequest: ByteCursor<SgxKeyRequest>): ByteArray {
        val keyOut = ByteArray(SgxKey128Bit.size)
        Native.getKey(keyRequest.buffer.getRemainingBytes(avoidCopying = true), keyOut)
        return keyOut
    }

    /**
     * @return true if the enclave was loaded in debug mode, i.e. its report's `DEBUG` flag is set, false otherwise.
     */
    private fun isDebugMode(): Boolean {
        val isEnclaveDebug = this.isEnclaveDebug
        return if (isEnclaveDebug == null) {
            val report = createReport(null, null)
            val result = report[body][attributes][flags].isSet(SgxEnclaveFlags.DEBUG)
            this.isEnclaveDebug = result
            result
        } else {
            isEnclaveDebug
        }
    }

    // Static enclave registration
    init {
        val alwaysInsideEnclave = object : EnclaveContext {
            override fun isInsideEnclave() = true
        }
        EnclaveContext.Companion::class.java.getDeclaredField("instance")
            .apply { isAccessible = true }
            .set(null, alwaysInsideEnclave)
    }
}
