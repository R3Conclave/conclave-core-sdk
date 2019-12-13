package com.r3.sgx.multiplex.enclave

import com.r3.sgx.core.enclave.internal.NativeEnclaveApi.ENCLAVE_CLASS_ATTRIBUTE_NAME
import com.r3.sgx.multiplex.common.hashKey
import com.r3.sgx.multiplex.common.sha256
import com.r3.sgx.utils.classloaders.MemoryClassLoader
import java.lang.IllegalStateException
import java.lang.ref.ReferenceQueue
import java.lang.ref.WeakReference
import java.nio.ByteBuffer
import java.util.Collections.singletonList
import java.util.HashMap
import java.util.jar.JarInputStream

data class EnclaveJar(val className: String, val classLoader: ClassLoader)

class EnclaveJarCache {
    private val jarCache = HashMap<String, WeakEnclaveReference>()
    private val expiryQueue = ReferenceQueue<EnclaveJar>()

    /**
     * @param hashKey The SHA-256 of the target enclave, as a canonical string.
     * @return The existing [EnclaveJar] data object for this enclave.
     */
    operator fun get(hashKey: String): EnclaveJar {
        return synchronized(jarCache) {
            // Remove the cache entries for enclave jars which have been garbage-collected.
            removeStaleCacheEntries()
            jarCache[hashKey]?.get()
                ?: throw IllegalArgumentException("No enclave with SHA-256 $hashKey")
        }
    }

    /**
     * @param jarData A memory buffer containing an enclave's "fat jar".
     * @param expectedKey The expected SHA-256 of [jarData], as a canonical string.
     * @return The [EnclaveJar] data object for this enclave.
     */
    fun createOrGet(jarData: ByteBuffer, expectedKey: String): EnclaveJar {
        val hashKey = jarData.sha256.hashKey
        require(expectedKey == hashKey) {
            "SHA-256 of enclave '$hashKey' does not match expected '$expectedKey'"
        }

        return synchronized(jarCache) {
            // Remove the cache entries for enclave jars which have been garbage-collected.
            removeStaleCacheEntries()

            jarCache[hashKey]?.get() ?: run {
                val enclaveURL = URLSchemes.createMultiplexURL("/enclave/$hashKey", jarData)
                val enclaveClassName = JarInputStream(enclaveURL.value.openStream()).use { jar ->
                    jar.manifest.mainAttributes.getValue(ENCLAVE_CLASS_ATTRIBUTE_NAME)
                        ?: throw IllegalStateException("Enclave class not specified. Expected $ENCLAVE_CLASS_ATTRIBUTE_NAME attribute in manifest")
                }

                /**
                 * The enclave "fat jar" contains both api-core-enclave and Kotlin.
                 * However, the dynamically loaded enclave must use the same copies
                 * of these classes as the multiplexing enclave, and so it requires
                 * the application classloader as its classloading parent.
                 */
                val enclaveJar = EnclaveJar(enclaveClassName, MemoryClassLoader(singletonList(enclaveURL)))
                jarCache[hashKey] = WeakEnclaveReference(hashKey, enclaveJar, expiryQueue)
                enclaveJar
            }
        }
    }

    /**
     * This is a "housekeeping" task that removes the cache entries whose
     * values have already been garbage-collected. The caller is assumed
     * already to be holding the monitor lock on [jarCache].
     */
    private fun removeStaleCacheEntries() {
        while (true) {
            val expired = expiryQueue.poll() ?: break
            if (expired is WeakEnclaveReference && jarCache[expired.hashKey] === expired) {
                jarCache.remove(expired.hashKey)
            }
        }
    }
}

/**
 * Value class for the "weak value map" JAR cache.
 */
private class WeakEnclaveReference(
    val hashKey: String,
    enclaveJar: EnclaveJar,
    queue: ReferenceQueue<EnclaveJar>
) : WeakReference<EnclaveJar>(enclaveJar, queue)
