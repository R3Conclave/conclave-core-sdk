package com.r3.conclave.host

import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.concurrent.TimeUnit
import kotlin.io.path.absolutePathString

object Gramine {

    private lateinit var processGramineDirect: Process

    fun start() {
        try {
            val gramineWorkingDirPath = Files.createTempDirectory("conclave-gramine-runtime")
            generateManifestFile(gramineWorkingDirPath)

            processGramineDirect = ProcessBuilder()
                .inheritIO()
                .directory(File(gramineWorkingDirPath.toUri()))
                .command("gramine-direct", "bash", "-c", "echo \"Gramine bash \'enclave\' started\" && sleep 10000")
                .start()
        } catch (exception: Exception) {
            exception.printStackTrace();
        }
    }

    fun stop() {
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
        Files.write(Paths.get(dirPath.absolutePathString() + "/" + "bash.manifest"), manifestContent.toByteArray())
    }

    private val manifestContent =
        """[loader]
entrypoint = "file:/usr/lib/x86_64-linux-gnu/gramine/libsysdb.so"
log_level = "error"
insecure__use_cmdline_argv = true

[libos]
entrypoint = "/usr/bin/bash"

[fs]
[[fs.mounts]]
path = "/lib"
uri = "file:/usr/lib/x86_64-linux-gnu/gramine/runtime/glibc"

[[fs.mounts]]
path = "/lib/x86_64-linux-gnu"
uri = "file:/lib/x86_64-linux-gnu"

[[fs.mounts]]
path = "/usr/lib"
uri = "file:/usr/lib"

[[fs.mounts]]
path = "/usr/bin"
uri = "file:/usr/bin"

[sgx]
debug = true
nonpie_binary = true
enclave_size = "512M"
thread_num = 4
allowed_files = [ "file:scripts/",]
isvprodid = 0
isvsvn = 0
remote_attestation = false
require_avx = false
require_avx512 = false
require_mpx = false
require_pkru = false
require_amx = false
support_exinfo = false
enable_stats = false
[[sgx.trusted_files]]
uri = "file:/usr/lib/x86_64-linux-gnu/gramine/libsysdb.so"

[[sgx.trusted_files]]
uri = "file:/usr/bin/"

[[sgx.trusted_files]]
uri = "file:/usr/lib/x86_64-linux-gnu/gramine/runtime/glibc/"

[[sgx.trusted_files]]
uri = "file:/lib/x86_64-linux-gnu/"

[[sgx.trusted_files]]
uri = "file:/usr//lib/x86_64-linux-gnu/"

[loader.env]
LD_LIBRARY_PATH = "/lib:/lib/x86_64-linux-gnu:/usr/lib/x86_64-linux-gnu"
PATH = "/usr/bin"
"""
}
