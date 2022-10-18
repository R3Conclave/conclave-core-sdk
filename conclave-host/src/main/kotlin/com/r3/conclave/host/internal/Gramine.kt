package com.r3.conclave.host.internal

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardCopyOption
import java.util.concurrent.TimeUnit
import kotlin.io.path.div
import kotlin.io.path.writeText


object Gramine {
    private lateinit var processGramineDirect: Process

    fun start() {
        val gramineJar = this::class.java.getResourceAsStream("/GramineJar.jar")
        val gramineWorkingDirPath = Files.createTempDirectory("conclave-gramine-runtime")
        gramineWorkingDirPath.toFile().deleteOnExit()
        generateManifestFile(gramineWorkingDirPath)

        Files.copy(gramineJar, gramineWorkingDirPath, StandardCopyOption.REPLACE_EXISTING)
        processGramineDirect = ProcessBuilder()
            .inheritIO()
            .directory(gramineWorkingDirPath.toFile())
            //.command("gramine-direct", "bash", "-c", """echo "Gramine bash 'enclave' started" && sleep 10000""")
            .command("gramine-direct", "java", "-jar", "$gramineJar")
            .start()
    }

    fun stop() {
        if (!::processGramineDirect.isInitialized) return
        processGramineDirect.destroy()
        processGramineDirect.waitFor(10L, TimeUnit.SECONDS)
        if (processGramineDirect.isAlive) {
            processGramineDirect.destroyForcibly()
        }
    }

    fun isGramineEnabled(): Boolean {
        val envValue = System.getenv("CONCLAVE_GRAMINE_ENABLE") ?: return false
        return envValue.uppercase() == "TRUE"
    }

    private fun generateManifestFile(dirPath: Path) {
        //val manifestFile = dirPath / "bash.manifest"
        val manifestFile = dirPath / "java.manifest"
        manifestFile.writeText(manifestContent)
    }

    private const val manifestContent =
        """[loader]
entrypoint = "file:/usr/lib/x86_64-linux-gnu/gramine/libsysdb.so"
log_level = "error"
insecure__use_cmdline_argv = true

[libos]
entrypoint = "/usr/lib/jvm/java-17-openjdk-amd64/bin/java"

[sys]
insecure__allow_eventfd = true

[sgx]
remote_attestation = false
enclave_size = "4G"
thread_num = 64
isvprodid = 0
isvsvn = 0
debug = false
require_avx = false
require_avx512 = false
require_mpx = false
require_pkru = false
require_amx = false
support_exinfo = false
nonpie_binary = false
enable_stats = false
[[sgx.trusted_files]]
uri = "file:/usr/lib/x86_64-linux-gnu/gramine/libsysdb.so"

[[sgx.trusted_files]]
uri = "file:/usr/lib/jvm/java-17-openjdk-amd64/bin/java"

[[sgx.trusted_files]]
uri = "file:/usr/lib/x86_64-linux-gnu/gramine/runtime/glibc/"

[[sgx.trusted_files]]
uri = "file:/lib/x86_64-linux-gnu/"

[[sgx.trusted_files]]
uri = "file:/usr//lib/x86_64-linux-gnu/"

[[sgx.trusted_files]]
uri = "file:/usr/lib/jvm/java-17-openjdk-amd64/lib/"

[[sgx.trusted_files]]
uri = "file:/usr/lib/jvm/java-17-openjdk-amd64/bin/java"

[[sgx.trusted_files]]
uri = "file:/usr/share/java/"

[[sgx.trusted_files]]
uri = "file:src/main/resources/GramineJar.jar

[[sgx.trusted_files]]
uri = "file:/usr/lib/jvm/java-17-openjdk-amd64/conf/security/java.security"

[fs]
[[fs.mounts]]
uri = "file:/usr/lib/x86_64-linux-gnu/gramine/runtime/glibc"
path = "/lib"

[[fs.mounts]]
uri = "file:/lib/x86_64-linux-gnu"
path = "/lib/x86_64-linux-gnu"

[[fs.mounts]]
uri = "file:/usr"
path = "/usr"

[[fs.mounts]]
uri = "file:/tmp"
path = "/tmp"

[[fs.mounts]]
uri = "file:build/libs"
path = "/spring-boot/build/libs"

[loader.env]
LD_LIBRARY_PATH = "/lib:/lib/x86_64-linux-gnu:/usr/lib:/usr//lib/x86_64-linux-gnu"
"""
}
