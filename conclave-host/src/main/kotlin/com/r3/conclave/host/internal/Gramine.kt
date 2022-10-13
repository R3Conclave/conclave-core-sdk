package com.r3.conclave.host.internal

import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.concurrent.TimeUnit
import kotlin.io.path.div
import kotlin.io.path.writeText

object Gramine {
    private lateinit var processGramineDirect: Process

    fun start() {
        val gramineWorkingDirPath = Files.createTempDirectory("conclave-gramine-runtime")
        gramineWorkingDirPath.toFile().deleteOnExit()
        copyManifestToTempDirectory(gramineWorkingDirPath)

        processGramineDirect = ProcessBuilder()
            .inheritIO()
            .directory(gramineWorkingDirPath.toFile())
            .command("gramine-direct", "bash", "-c", """echo "Gramine bash 'enclave' started" && sleep 10000""")
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

    private fun copyManifestToTempDirectory(dirPath: Path) {
        val manifestFile = dirPath / "bash.manifest"
        Files.copy(Paths.get("/conclave/gramine/bash.manifest"), manifestFile)
    }
}
