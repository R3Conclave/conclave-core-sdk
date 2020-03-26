package com.r3.sgx.dynamictesting

import com.r3.sgx.core.enclave.Enclave
import org.junit.rules.ExternalResource
import java.io.File
import java.nio.file.Path
import java.time.Duration
import java.time.Instant
import java.util.*
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit.SECONDS

/**
 * A JUnit rule that allows on-the-fly creation of enclaves.
 */
class TestEnclaves : ExternalResource() {
    private lateinit var executorService: ExecutorService
    private var cache: Cache? = null
    private var entropy: Long? = null

    public override fun before() {
        executorService = Executors.newFixedThreadPool(4)
        val cacheDirectory = File("build/cache")
        cacheDirectory.deleteRecursively()
        cache = Cache(cacheDirectory, executorService)
        val random = Random()
        entropy = random.nextLong()
    }

    public override fun after() {
        executorService.apply {
            shutdown()
            if (!awaitTermination(30, SECONDS)) {
                shutdownNow()
            }
        }
        cache = null
    }

    private fun createEnclaveJar(entryClass: Class<*>, builder: EnclaveBuilder): Cached<File> {
        return Cached.singleFile("${entryClass.name}-$entropy-${builder.type.name}", "${entryClass.simpleName}.jar") { output ->
            BuildEnclaveJarFromClass.buildEnclaveJarFromClass(entryClass, builder.includeClasses, output)
        }
    }

    fun getEnclaveJar(enclaveClass: Class<out Enclave>, enclaveBuilder: EnclaveBuilder): File {
        return cache ?.let { it[createEnclaveJar(enclaveClass, enclaveBuilder)] }
                ?: throw IllegalStateException("Add @Rule or @ClassRule to your ${TestEnclaves::class.java.simpleName} field")
    }

    fun getEnclaveJar(enclaveClass: Class<out Enclave>): File {
        return getEnclaveJar(enclaveClass, EnclaveBuilder())
    }

    fun getEnclave(entryClass: Class<*>): File {
        return getEnclave(entryClass, EnclaveBuilder())
    }

    fun getEnclave(entryClass: Class<*>, builder: EnclaveBuilder): File {
        if (cache == null) {
            throw IllegalStateException("Add @Rule or @ClassRule to your ${TestEnclaves::class.java.simpleName} field")
        }
        val start = Instant.now()
        val cachedSignedEnclave = signedEnclaveFile(entryClass, builder)
        val enclave = cache!![cachedSignedEnclave]
        val end = Instant.now()
        println(Duration.between(start, end))

        return enclave
    }

    private fun signedEnclaveFile(entryClass: Class<*>, builder: EnclaveBuilder): Cached<File> {
        val cachedJar = createEnclaveJar(entryClass, builder)
        val cachedEnclave = BuildEnclave.buildEnclave(builder.type, cachedJar)
        val cachedKey = builder.key?.let { Cached.Pure(it) } ?: SignEnclave.createDummyKey()
        val cachedConfig = SignEnclave.createConfig(builder.config)
        return SignEnclave.signEnclave(inputKey = cachedKey, inputEnclave = cachedEnclave, enclaveConfig = cachedConfig)
    }

    fun getEnclaveMetadata(enclaveClass: Class<*>, builder: EnclaveBuilder): Path {
        val cachedSignedEnclave = signedEnclaveFile(enclaveClass, builder)
        val metadataFile = SignEnclave.enclaveMetadata(cachedSignedEnclave)
        return cache!![metadataFile].toPath()
    }
}

data class EnclaveBuilder(
        val config: EnclaveConfig = EnclaveConfig(),
        val type: EnclaveType = EnclaveType.Simulation,
        val key: File? = null,
        val includeClasses: List<Class<*>> = emptyList()
) {
    fun withConfig(config: EnclaveConfig) = copy(config = config)
    fun withType(type: EnclaveType) = copy(type = type)
    fun withKey(key: File) = copy(key = key)
    fun withClass(clazz: Class<*>) = copy(includeClasses = includeClasses + clazz)
}
