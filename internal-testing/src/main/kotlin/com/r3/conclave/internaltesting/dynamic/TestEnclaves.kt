package com.r3.conclave.internaltesting.dynamic

import com.r3.conclave.common.EnclaveMode
import com.r3.conclave.enclave.Enclave
import com.r3.conclave.host.EnclaveHost
import com.r3.conclave.host.MailCommand
import com.r3.conclave.host.internal.createHost
import org.junit.jupiter.api.extension.AfterAllCallback
import org.junit.jupiter.api.extension.BeforeAllCallback
import org.junit.jupiter.api.extension.ExtensionContext
import java.io.File
import java.nio.file.Path
import java.util.*
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit.SECONDS
import java.util.function.Consumer

class TestEnclaves : BeforeAllCallback, AfterAllCallback {
    private lateinit var executorService: ExecutorService
    private var _cache: Cache? = null
    private var entropy: Long? = null

    override fun beforeAll(context: ExtensionContext) {
        executorService = Executors.newFixedThreadPool(4)
        val cacheDirectory = File("build/cache")
        cacheDirectory.deleteRecursively()
        _cache = Cache(cacheDirectory, executorService)
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
        _cache = null
    }

    private val cache: Cache
        get() {
            return checkNotNull(_cache) {
                "Add @RegisterExtension to your ${TestEnclaves::class.java.simpleName} field"
            }
        }

    private fun createEnclaveJar(entryClass: Class<out Enclave>, builder: EnclaveBuilder): Cached<File> {
        return Cached.singleFile("${entryClass.name}-$entropy", "${entryClass.simpleName}.jar") { output ->
            BuildEnclaveJar.build(entryClass, builder.includeClasses, output.toPath())
        }
    }

    fun getEnclaveJar(enclaveClass: Class<out Enclave>, enclaveBuilder: EnclaveBuilder = EnclaveBuilder()): File {
        return cache[createEnclaveJar(enclaveClass, enclaveBuilder)]
    }

    fun getSignedEnclaveFile(
        entryClass: Class<out Enclave>,
        builder: EnclaveBuilder = EnclaveBuilder(),
        keyGenInput: String? = null
    ): File {
        val cachedSignedEnclave = signedEnclaveFile(entryClass, builder, keyGenInput)
        return cache[cachedSignedEnclave]
    }

    private fun signedEnclaveFile(
        entryClass: Class<out Enclave>,
        builder: EnclaveBuilder,
        keyGenInput: String? = null
    ): Cached<File> {
        val cachedJar = createEnclaveJar(entryClass, builder)
        val cachedEnclave = BuildEnclave.buildEnclave(builder.type, cachedJar)
        val cachedKey = builder.key?.let { Cached.Pure(it) } ?: SignEnclave.createDummyKey()
        val cachedConfig = SignEnclave.createConfig(builder.config)
        return SignEnclave.signEnclave(
            inputKey = if (keyGenInput == null) cachedKey else SignEnclave.createDummyKey(keyGenInput),
            inputEnclave = cachedEnclave,
            enclaveConfig = cachedConfig
        )
    }

    inline fun <reified T : Enclave> hostTo(enclaveBuilder: EnclaveBuilder = EnclaveBuilder()): EnclaveHost {
        return hostTo(T::class.java, enclaveBuilder)
    }

    fun hostTo(entryClass: Class<out Enclave>, enclaveBuilder: EnclaveBuilder = EnclaveBuilder()): EnclaveHost {
        val mode = when (enclaveBuilder.type) {
            EnclaveType.Simulation -> EnclaveMode.SIMULATION
            EnclaveType.Debug -> EnclaveMode.DEBUG
            EnclaveType.Release -> EnclaveMode.RELEASE
        }
        val enclaveFile = getSignedEnclaveFile(entryClass, enclaveBuilder).toPath()
        return createHost(mode, enclaveFile, entryClass.name, tempFile = false)
    }

    fun getEnclaveMetadata(enclaveClass: Class<out Enclave>, builder: EnclaveBuilder): Path {
        val cachedSignedEnclave = signedEnclaveFile(enclaveClass, builder)
        val metadataFile = SignEnclave.enclaveMetadata(cachedSignedEnclave)
        return cache[metadataFile].toPath()
    }
}

data class EnclaveBuilder(
    val config: EnclaveConfig = EnclaveConfig(),
    val type: EnclaveType = EnclaveType.Simulation,
    val key: File? = null,
    val includeClasses: List<Class<*>> = emptyList(),
    val mailCallback: Consumer<List<MailCommand>>? = null
)
