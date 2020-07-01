package com.r3.conclave.dynamictesting

import com.r3.conclave.common.EnclaveMode
import com.r3.conclave.enclave.Enclave
import com.r3.conclave.host.EnclaveHost
import com.r3.conclave.host.internal.createHost
import org.junit.jupiter.api.extension.AfterAllCallback
import org.junit.jupiter.api.extension.BeforeAllCallback
import org.junit.jupiter.api.extension.ExtensionContext
import java.io.File
import java.nio.file.Path
import java.time.Duration
import java.time.Instant
import java.util.*
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit.SECONDS

class TestEnclaves : BeforeAllCallback, AfterAllCallback {
    private lateinit var executorService: ExecutorService
    private var cache: Cache? = null
    private var entropy: Long? = null

    override fun beforeAll(context: ExtensionContext) {
        executorService = Executors.newFixedThreadPool(4)
        val cacheDirectory = File("build/cache")
        cacheDirectory.deleteRecursively()
        cache = Cache(cacheDirectory, executorService)
        val random = Random()
        entropy = random.nextLong()
    }

    override fun afterAll(context: ExtensionContext) {
        executorService.apply {
            shutdown()
            if (!awaitTermination(30, SECONDS)) {
                shutdownNow()
            }
        }
        cache = null
    }

    private fun createEnclaveJar(entryClass: Class<out Enclave>, builder: EnclaveBuilder): Cached<File> {
        return Cached.singleFile("${entryClass.name}-$entropy-${builder.type.name}", "${entryClass.simpleName}.jar") { output ->
            BuildEnclaveJar.build(entryClass, builder.includeClasses, output.toPath())
        }
    }

    fun getEnclaveJar(enclaveClass: Class<out Enclave>, enclaveBuilder: EnclaveBuilder = EnclaveBuilder()): File {
        val cache = checkNotNull(cache) {
            "Add @RegisterExtension to your ${TestEnclaves::class.java.simpleName} field"
        }
        return cache[createEnclaveJar(enclaveClass, enclaveBuilder)]
    }

    fun getSignedEnclaveFile(entryClass: Class<out Enclave>, builder: EnclaveBuilder = EnclaveBuilder(), keyGenInput: String? = null): File {
        val cache = checkNotNull(cache) {
            "Add @RegisterExtension to your ${TestEnclaves::class.java.simpleName} field"
        }
        val start = Instant.now()
        val cachedSignedEnclave = signedEnclaveFile(entryClass, builder, keyGenInput)
        val enclave = cache[cachedSignedEnclave]
        val end = Instant.now()
        println(Duration.between(start, end))

        return enclave
    }

    private fun signedEnclaveFile(entryClass: Class<out Enclave>, builder: EnclaveBuilder, keyGenInput: String? = null): Cached<File> {
        val cachedJar = createEnclaveJar(entryClass, builder)
        val cachedEnclave = BuildEnclave.buildEnclave(builder.type, cachedJar)
        val cachedKey = builder.key?.let { Cached.Pure(it) } ?: SignEnclave.createDummyKey()
        val cachedConfig = SignEnclave.createConfig(builder.config)
        return SignEnclave.signEnclave(
                inputKey = if (keyGenInput == null) cachedKey else SignEnclave.createDummyKey(keyGenInput),
                inputEnclave = cachedEnclave,
                enclaveConfig = cachedConfig)
    }

    inline fun <reified T : Enclave> hostTo(enclaveBuilder: EnclaveBuilder = EnclaveBuilder()): EnclaveHost {
        val enclaveFile = getSignedEnclaveFile(T::class.java, enclaveBuilder).toPath()
        return createHost(EnclaveMode.SIMULATION, enclaveFile, T::class.java.name, tempFile = false)
    }

    fun getEnclaveMetadata(enclaveClass: Class<out Enclave>, builder: EnclaveBuilder): Path {
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
